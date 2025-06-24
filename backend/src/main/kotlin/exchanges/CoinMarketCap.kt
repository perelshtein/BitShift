package com.github.perelshtein.exchanges

import com.github.perelshtein.database.CmcCurrency
import com.github.perelshtein.database.CourseManager
import com.github.perelshtein.database.Exchange
import com.github.perelshtein.database.ExchangeManager
import com.github.perelshtein.database.FormulaManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.collections.filter
import kotlin.collections.flatMap
import kotlin.getValue

// Это резервная биржа, исп. в основном для проверки курса при добавлении.
// Но можно использовать и для обновления курсов.
// Количество бесплатных запросов ограничено.
class CoinMarketCap: KoinComponent, ExchangeAPI {
    val mgr: ExchangeManager by inject()
    val courseMgr: CourseManager by inject()
    val client: HttpClient by inject()
    val CMC = CoinMarketCap::class.simpleName!!
    val log = LoggerFactory.getLogger(CMC)
    private var exchangeInfo: Exchange
    private var cachedPrice = mutableMapOf<String, Pair<Double, LocalDateTime>>()

    init {
        if(mgr.getExchangeByName(CMC) == null) {
            mgr.addExchange(Exchange {
                name = CoinMarketCap::class.simpleName!!
                updatePeriod = 1
                maxFailCount = 5
                isEnabled = true
                url = "https://pro-api.coinmarketcap.com"
                lastUpdate = LocalDateTime.now().minusMinutes(2L)
            })
        }
        exchangeInfo = mgr.getExchangeByName(CMC)!!
    }

    suspend fun getReferencePrice(baseCurrency: String, quoteCurrency: String): Double? {
        runCatching {
            val now = LocalDateTime.now()
            cachedPrice["${baseCurrency}$quoteCurrency"]?.let {
                if(now.isBefore(it.second.plusMinutes(1L))) {
                    log.debug("Используем цену из кэша для ${baseCurrency}-${quoteCurrency}")
                    return it.first
                }
            }
            val apiKey = mgr.getApiKeys(exchangeInfo.id)?.apiKey ?: ""

            // Находим ID базовой валюты
            exchangeInfo = mgr.getExchangeByName(CMC)!!
            if(exchangeInfo.isEnabled == false) return null
            var baseCurrencyInfo = courseMgr.getCmcCurrencyByName(baseCurrency)
            if(baseCurrencyInfo == null) {
                log.debug("Получаем id для ${baseCurrency}..")
                val response = client.get("${exchangeInfo.url}/v1/cryptocurrency/map") {
                    url {
                        headers.append("X-CMC_PRO_API_KEY", apiKey)
                        headers.append("Accept", "application/json")
                        parameters.append("symbol", baseCurrency)
                    }
                }
                if (response.status != HttpStatusCode.OK) {
                    val details = response.body<ErrorResult>()
                    throw Exception(
                        "Ответ сервера: ${details.status}\n" +
                                "Код ошибки HTTP: ${response.status}"
                    )
                }

                response.body<CoinMarketCapCurrencyIds>()
                    .data.firstOrNull()?.let {
                        baseCurrencyInfo = CmcCurrency {
                            cmcId = it.id
                            name = it.symbol
                        }
                        courseMgr.addCmcCurrency(baseCurrencyInfo)
                    }
            }

            // Загружаем цену по ID
            log.debug("Загружаю цену для ${baseCurrency}-${quoteCurrency}..")
            baseCurrencyInfo?.cmcId?.let { id ->
                val response = client.get("${exchangeInfo.url}/v2/tools/price-conversion") {
                    url {
                        headers.append("X-CMC_PRO_API_KEY", apiKey)
                        headers.append("Accept", "application/json")
                        parameters.append("id", "${id}")
                        parameters.append("convert", quoteCurrency)
                        parameters.append("amount", "1")
                    }
                }
                if (response.status != HttpStatusCode.OK) {
                    val details = response.body<ErrorResult>()
                    throw Exception(
                        "Ответ сервера: ${details.status}\n" +
                                "Код ошибки HTTP: ${response.status}"
                    )
                }
                val answer2 = response.body<CoinMarketCapPrice>()
                    .data

                answer2.quote[quoteCurrency]
                    ?.let {
                        cachedPrice["${baseCurrency}$quoteCurrency"] = Pair(it.price, now)
                        log.debug("Цена ${baseCurrency}-${quoteCurrency} успешно загружена")
                        return it.price
                    }
            }
        }
            .getOrElse { exception ->
                val msg = exception.message ?: "Неизвестная ошибка"
                log.error("Не удалось проверить цену.\n${msg}")
            }
        return null
    }

    override suspend fun fetchCodes(): Set<String> {
        // Список валют - только те валюты, которые используются в формулах
        val formulaMgr: FormulaManager by inject()
        val formulas = formulaMgr.getFormulas(filter = "CoinMarketCap")
        log.info("Загружено ${formulas.second.size} формул")
        return formulas
            .second
            .flatMap { listOf(it.from, it.to) }
            .toSet()
    }

    fun fetchCodePairs(): Set<String> {
        val formulaMgr: FormulaManager by inject()
        val formulas = formulaMgr.getFormulas(filter = "CoinMarketCap")
        return formulas
            .second
            .map { "${it.from}-${it.to}" }
            .toSet()
    }

    override suspend fun fetchCourses(): List<Course> {
        // асинхронно запустим несколько потоков для загрузки курсов
        val scope = CoroutineScope(Dispatchers.IO)
        val res = fetchCodePairs().map {
            scope.async {
                val currencyFrom = it.split("-").first()
                val currencyTo = it.split("-").last()
                val price = getReferencePrice(currencyFrom, currencyTo) ?: 0.0
                Course(from = currencyFrom, to = currencyTo, price = price)
            }
        }
        log.info("Загружаем ${res.size} курсов")
        return res.map { it.await() } //если какой-нибудь поток отвалится, загрузим хотя бы то, что есть
    }
}

@Serializable
data class CoinMarketCapPrice(
    val data: Currency
) {
    @Serializable
    data class Currency(
        val id: Int,
        val name: String,
        val symbol: String,
        val quote: Map<String, Quote>
    ) {
        @Serializable
        data class Quote(
            val price: Double
        )
    }
}

@Serializable
data class ErrorResult(
    val status: Status
) {
    @Serializable
    data class Status(
        val error_code: Int,
        val error_message: String
    )
}

@Serializable
data class CoinMarketCapCurrencyIds(
    val data: List<Currency>
) {
    @Serializable
    data class Currency(
        val id: Int,
//        val name: String,
        val symbol: String,
    )
}