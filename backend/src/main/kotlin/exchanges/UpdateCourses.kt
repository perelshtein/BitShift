package com.github.perelshtein.exchanges

import com.github.perelshtein.database.CourseDB
import com.github.perelshtein.database.CourseManager
import com.github.perelshtein.database.ExchangeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.collections.associate
import kotlin.collections.filter
import kotlin.collections.map

interface ExchangeAPI {
    suspend fun fetchCodes(): Set<String> // загрузка кодов валют из сети
    suspend fun fetchCourses(): List<Course> // загрузка курсов из сети
}

interface BalanceAPI {
    suspend fun fetchBalance(): Double // загрузка резерва, доступного для создания заявок, с биржи
}

data class Course(
    val from: String,
    val to: String,
    val price: Double = 0.0,
    val buy: Double = 0.0,
    val sell: Double = 0.0
)

class UpdateCourses: KoinComponent {
//    private val garantex: Garantex by inject()
    private val bybit: Bybit by inject()
    private val binance: Binance by inject()
    private val coinMarketCap: CoinMarketCap by inject()
    private val cbr: Cbr by inject()
    private val mexc: Mexc by inject()
    private val mgr: ExchangeManager by inject()
    private val courseMgr: CourseManager by inject()
    private val log = LoggerFactory.getLogger("UpdateCourses")

    // Объект -> Настройки из базы
    private val exchangeList = listOf(bybit, binance, coinMarketCap, cbr, mexc)

    // Имя -> Количество ошибок
    private val failCount = exchangeList
        .associate { it::class.simpleName!! to 0 }
        .toMutableMap()

    fun update(idToUpdate: Int? = null): List<Job> {
        val scope = CoroutineScope(Dispatchers.IO)
        val now = LocalDateTime.now()
        var isUpdated = false
        val exchanges = exchangeList
            .associate { it as ExchangeAPI to mgr.getExchangeByName(it::class.simpleName!!) }
            .filterValues {
                // Обновим или все включенные биржи, или одну выбранную (если нажали Обновить)
                if(idToUpdate == null) it != null && it.isEnabled
                else it != null && it.id == idToUpdate
            }

        if (exchanges.isEmpty()) throw Exception("Список бирж пуст!")

        return exchanges.map { (k, v) ->
            // обновим курсы в параллельном режиме для каждой биржи
            val name = k::class.simpleName
            scope.launch {
                try {
                    //если уже подошло время, обновим курсы
                    if (v == null) {
                        throw Exception("База данных для биржи ${name} не инициализирована")
                    }
                    // 1) обновим, если горит, прямо сейчас!
                    // 2) не горит? проверим время:
                    // вычитаем 1 секунду, т.к. шаг задается в минутах, и обновл проверяется раз в минуту,
                    // может быть now + 1мин == lastUpdate
                    if (idToUpdate != null || now.isAfter(v.lastUpdate.plusMinutes(v.updatePeriod.toLong()).minusSeconds(1))) {

                        // Загрузим список цен с биржи
                        log.info("Обновляем курсы для ${name}...")
                        var courses = k.fetchCourses()
                            .filter { it.buy > 0.0 || it.price > 0.0 }
                        val netCoursesSize = courses.size
                        if(netCoursesSize == 0) {
                            courseMgr.dropTradeCurrencies(v.id)
                            courseMgr.dropCourses(v.id)
                            log.info("Нет курсов для ${name}")
                            return@launch
                        }

                        // Загрузим список валют из базы
                        var codes = courseMgr.getTradeCurrencies(v.id)

                        // Вытащим названия валют
                        val usedCodes = getUsedCodes(courses, codes)

                        // Если количество валют поменялось, или он пуст - загрузим с биржи
                        if(codes != usedCodes || codes.isEmpty()) {
                            val codesToDelete = codes - usedCodes
                            if (codesToDelete.size > 0) log.info(
                                "Лишние валюты для ${name}: ${
                                    codesToDelete.joinToString(
                                        ", "
                                    )
                                }"
                            )
                            log.info("Обновляем список валют для ${name}...")
                            codes = k.fetchCodes()
                            require(codes.size > 0, { "Нет кодов валют для ${name}" })
                            log.info("Получено ${codes.size} валют для ${name}")
                            courseMgr.dropTradeCurrencies(v.id)
                            courseMgr.addTradeCurrencies(v.id, codes)
                        }

                        // Построим курсы. Расшифруем BTCUSDT -> BTC, USDT
                        // считаем только те, которые удалось расшифровать
                        courses = buildCourses(courses, codes - Json.decodeFromString<List<String>>(v.blacklist))
                        log.info("Получено ${courses.size} курсов для ${name}")

                        //Найдем обратные курсы - BTC/USDT -> USDT/BTC, если их нет
                        courses = ensureReverseCourses(courses)
                        val reverseCoursesSize = courses.size - netCoursesSize
                        log.info("Добавлено ${reverseCoursesSize} обратных курсов для ${name}")

                        //почистим базу, если количество валют изменилось
                        if (courseMgr.getCoursesCount(v.id) != courses.size) {
                            courseMgr.dropCourses(v.id)
                        }

                        // обновим курсы в базе
                        val existingCourses = courseMgr.getCoursesMap(v.id)
                        val coursesToUpsert = mutableListOf<CourseDB>()
                        courses.forEach {
                            val existingCourse = existingCourses[it.from to it.to]
                            if (existingCourse != null) {
                                coursesToUpsert.add(existingCourse.apply {
                                    price = it.price
                                    buy = it.buy
                                    sell = it.sell
                                })
                            } else {
                                coursesToUpsert.add(CourseDB {
                                    from = it.from
                                    to = it.to
                                    price = it.price
                                    buy = it.buy
                                    sell = it.sell
                                    exchangeId = v.id
                                    tag = listOfNotNull(
                                        name,
                                        it.from,
                                        it.to,
                                        if (it.buy > 0.0) "buy" else null
                                    ).joinToString("_")
                                })
                            }
                        }
                        courseMgr.upsertCourses(coursesToUpsert)
                        log.debug("Обновлены записи в базе для ${name}")

                        v.lastUpdate = now
                        failCount[name!!] = 0
                        isUpdated = true
                        log.info("Курсы для ${name} обновлены")
                    }
                } catch (exception: Exception) {
                    // проверим счетчик ошибок. если он превышен - обнулим курсы
                    failCount[name!!]?.let {
                        if(it >= v!!.maxFailCount) {
                            log.error("Обнуляем курсы для ${name}")
                            courseMgr.clearPrice(v!!.id)
                        }
                    }
                    failCount[name] = (failCount[name] ?: 0) + 1

                    log.error(exception.message ?: "Неизвестная ошибка", exception)
                } finally {
                    if (v != null) mgr.updateExchange(v)
                }
            }
        }
    }

    private fun ensureReverseCourses(courses: List<Course>): List<Course> {
        // Создадим Set для быстрого поиска
        val existingPairs = courses.map { it.from to it.to }.toSet()

        // Посчитаем обратные курсы и добавим их, если их нет
        val additionalCourses = courses.mapNotNull { course ->
            val reversePair = course.to to course.from
            if (reversePair !in existingPairs) {
                Course(
                    from = course.to,
                    to = course.from,
                    // Для расчета обратного курса меняем местами Buy и Sell
                    price = if (course.price != 0.0) 1 / course.price else 0.0,
                    buy = if (course.sell != 0.0) 1 / course.sell else 0.0,
                    sell = if (course.buy == 0.0 && course.price == 0.0) {
                        0.0
                    } else {
                        // Для обратного курса:
                        // если у нас есть только price, не заполняем sell
                        val sellPrice = course.sell.takeIf { it > 0.0 } ?: course.price
                        val spread = if (course.buy > 0.0) (sellPrice - course.buy) / course.buy * 100 else 0.0
                        val reverse = if (sellPrice > 0.0) 1 / sellPrice else 0.0
                        if (course.sell > 0.0) reverse + reverse * spread / 100 else 0.0
                    }
                )
            } else {
                null
            }
        }
        log.debug("Добавлены ${additionalCourses.size} обратных курсов")

        return courses + additionalCourses
    }

    // отфильтруем курсы с разрешенными кодами валют
    private fun buildCourses(courses: List<Course>, codes: Set<String>): List<Course> {
        return courses.mapNotNull { course ->
            // все просто. используем поле to
            if(course.to.isNotEmpty()) {
                if(course.from in codes && course.to in codes) {
                    return@mapNotNull course
                }
            }

            // пытаемся разбить строку на 2 существующие валюты
            else {
                val pair = course.from
                val splitPoint = (1 until pair.length).find { splitIndex ->
                    val currency1 = pair.substring(0, splitIndex)
                    val currency2 = pair.substring(splitIndex)
                    currency1 in codes && currency2 in codes
                }

                // Если нашли, вернем обновленный объект Course
                splitPoint?.let {
                    val currency1 = pair.substring(0, it)
                    val currency2 = pair.substring(it)
                    return@mapNotNull Course(from = currency1, to = currency2, buy = course.buy, sell = course.sell, price = course.price)
                }
            }

            // ничего не нашли
            null
        }
    }

    private fun getUsedCodes(courses: List<Course>, codes: Set<String>): Set<String> {
        val set1 = courses.filter { it.to.isNotEmpty() }
            .flatMap { listOf(it.from, it.to) }
            .toSet()

        val set2 = courses.filter { it.to.isEmpty() }
            .map { it.from }
            .flatMap { pair ->
                (1 until pair.length).mapNotNull { splitIndex ->
                    val currency1 = pair.substring(0, splitIndex)
                    val currency2 = pair.substring(splitIndex)
                    if (currency1 in codes && currency2 in codes && currency1 != currency2) {
                        listOf(currency1, currency2)
                    } else {
                        null
                    }
                }.flatten()
            }.toSet()

        return set1 + set2
    }
}