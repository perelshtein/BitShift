package com.github.perelshtein.database

import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.count
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.inList
import org.ktorm.dsl.like
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.mapNotNull
import org.ktorm.dsl.notEq
import org.ktorm.dsl.notInList
import org.ktorm.dsl.or
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.ktorm.dsl.whereWithConditions
import org.ktorm.entity.Entity
import org.ktorm.entity.add
import org.ktorm.entity.filter
import org.ktorm.entity.first
import org.ktorm.entity.firstOrNull
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.update
import org.ktorm.schema.Table
import org.ktorm.schema.double
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.ktorm.support.mysql.bulkInsert
import org.ktorm.support.mysql.bulkInsertOrUpdate
import org.slf4j.LoggerFactory
import kotlin.getValue

class CourseManager: KoinComponent {
    object Courses : Table<Course>("courses") {
        val id = int("id").primaryKey().bindTo { it.id }
        val from = varchar("currency_from").bindTo { it.from }
        val to = varchar("currency_to").bindTo { it.to }
        val exchangeId = int("exchange_id").bindTo { it.exchangeId }
        val price = double("price").bindTo { it.price }
        val buy = double("buy").bindTo { it.buy }
        val sell = double("sell").bindTo { it.sell }
        val tag = varchar("tag").bindTo { it.tag }
    }

    object TradeCurrencies : Table<TradeCurrency>("trade_currencies") {
        val id = int("id").primaryKey().bindTo { it.id }
        val exchangeId = int("exchange_id").bindTo { it.exchangeId }
        val name = varchar("name").bindTo { it.name }
    }

    object CmcCurrencies : Table<CmcCurrency>("cmc_currencies") {
        val id = int("id").primaryKey().bindTo { it.id }
        val cmc_id = int("cmc_id").primaryKey().bindTo { it.cmcId }
        val name = varchar("name").bindTo { it.name }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    private val log = LoggerFactory.getLogger("CourseManager")
    val Database.courses get() = this.sequenceOf(Courses)
    val Database.tradeCurrencies get() = this.sequenceOf(TradeCurrencies)
    val Database.cmcCurrencies get() = this.sequenceOf(CmcCurrencies)
    val mgr: ExchangeManager by inject()

    fun clearPrice(exchangeId: Int) {
        val list = db.from(Courses)
            .select()
            .where { Courses.exchangeId eq exchangeId }
            .map { Courses.createEntity(it).apply {
                this.price = 0.0
                this.buy = 0.0
                this.sell = 0.0
            } }
        upsertCourses(list)
    }

    fun getCourse(exchangeId: Int, from: String, to: String): Course? {
        return db.from(Courses)
            .select()
            .where { (Courses.exchangeId eq exchangeId) and (Courses.from eq from) and (Courses.to eq to) }
            .map {
                Courses.createEntity(it)
            }.firstOrNull()
    }

    fun getCourse(id: Int): Course? {
        return db.from(Courses)
            .select()
            .where { Courses.id eq id }
            .map {
                Courses.createEntity(it)
            }.firstOrNull()
    }

    fun getCoursesMap(exchangeId: Int): Map<Pair<String, String>, Course> {
        val exchInfo = mgr.getExchangeById(exchangeId) ?: throw Exception("Неизвестная биржа с id=$exchangeId")

        return db.from(Courses)
            .select()
            .whereWithConditions {
                it += Courses.exchangeId eq exchangeId
                it += (Courses.price notEq 0.0) or (Courses.buy notEq 0.0) or (Courses.sell notEq 0.0)
                val blacklist = exchInfo.toDTO().blacklist
                if (blacklist.isNotEmpty()) {
                    it += (Courses.from notInList blacklist) and (Courses.to notInList blacklist)
                }
            }
            .map { it[Courses.from]!! to it[Courses.to]!! to Courses.createEntity(it) }
            .toMap()
    }

    fun upsertCourses(courses: List<Course>) {
        if (courses.isEmpty()) return
        log.debug("upsertCourses starts..")

        db.bulkInsertOrUpdate(Courses) {
            courses.forEach { course ->
                item {
                    set(it.id, course.id)
                    set(it.exchangeId, course.exchangeId)
                    set(it.from, course.from)
                    set(it.to, course.to)
                    set(it.price, course.price)
                    set(it.buy, course.buy)
                    set(it.sell, course.sell)
                    set(it.tag, course.tag)
                }
            }
            onDuplicateKey {
                // values - это ссылка на excluded значения, которые указаны в запросе insert
                set(it.price, values(it.price))
                set(it.buy, values(it.buy))
                set(it.sell, values(it.sell))
            }
        }
        log.debug("upsertCourses ends")
    }

    fun getCourses(
        start: Int = 0,
        count: Int = 100,
        filter: String? = null,
        exchange: String? = null
    ): Pair<Int, List<Course>> {
        val query = db.from(Courses)
            .select()
            .whereWithConditions {
                if (filter != null) {
                    it += (Courses.from like "%$filter%") or (Courses.to like "%$filter%") or (Courses.tag like "%$filter%")
                }
                if (exchange != null) {
                    val exchInfo = mgr.getExchangeByName(exchange) ?: throw Exception("Неизвестная биржа $exchange")
                    it += Courses.exchangeId eq exchInfo.id
                    val blacklist = exchInfo.toDTO().blacklist
                    if (blacklist.isNotEmpty()) {
                        it += (Courses.from notInList blacklist) and (Courses.to notInList blacklist)
                    }
                }
            }

        val countFound = query.totalRecordsInAllPages
        val result = query.limit(start, count)
            .map {
                Courses.createEntity(it)
            }
        return countFound to result
    }

    fun getPriceByTag(tag: String): PriceSearchResult? {
        //если курс из файла, загрузим немедленно
        if(tag.startsWith("fromFile")) {

        }

        return db.courses.firstOrNull { it.tag eq tag }?.let {
            if(it.buy > 0.0 && it.sell > 0.0) {
                val spread = (it.sell - it.buy) / it.buy * 100
                return PriceSearchResult(spread, it.buy, tag)
            }
            else if(it.price != null) {
                return PriceSearchResult(0.0, it.price, tag)
            }
            else return null
        }
    }

    // ищем курс с наименьшим спредом
    // только среди включенных бирж!
    fun getPriceAndTag(from: String, to: String): PriceSearchResult? {
        val exchangeMgr: ExchangeManager by inject()
        val enabledExchanges = exchangeMgr.getEnabledExchanges()
        return db.from(Courses)
            .select(Courses.tag, Courses.buy, Courses.sell, Courses.price)
            .where { (Courses.exchangeId inList (enabledExchanges)) and (Courses.from eq from) and (Courses.to eq to) }
            .mapNotNull { row ->
                val tag = row[Courses.tag] ?: "no_tag"
                val buy = row[Courses.buy] ?: 0.0
                val sell = row[Courses.sell] ?: 0.0
                val price = row[Courses.price] ?: 0.0
                if (buy != 0.0 && sell != 0.0) {
                    val spread = (sell - buy) / buy * 100
                    Triple(tag, spread, buy)
                } else if (price != 0.0) {
                    Triple(tag, Double.MAX_VALUE, price) // значение price брать в последнюю очередь
                } else null
            }
            .minByOrNull { it.second }
            ?.let { (tag, spread, price) ->
                return PriceSearchResult(spread, price, tag)
            }
    }


    fun getCoursesCount(exchangeId: Int): Int {
        return db.from(Courses)
            .select(count())
            .where { Courses.exchangeId eq exchangeId }
            .map { row -> row.getInt(1) }
            .first()
    }

    fun dropCourses(exchangeId: Int) {
        db.delete(Courses) {
            Courses.exchangeId eq exchangeId
        }
    }

    fun getTradeCurrencies(exchangeId: Int): Set<String> {
        return db.from(TradeCurrencies)
            .select(TradeCurrencies.name)
            .where { TradeCurrencies.exchangeId eq exchangeId }
            .map { it[TradeCurrencies.name]!! }
            .toSet()
    }

    fun dropTradeCurrencies(exchangeId: Int) {
        db.delete(TradeCurrencies) {
            TradeCurrencies.exchangeId eq exchangeId
        }
    }

    fun getCmcCurrencyByName(name: String): CmcCurrency? {
        return db.from(CmcCurrencies)
            .select()
            .where { CmcCurrencies.name eq name }
            .map { CmcCurrencies.createEntity(it) }
            .firstOrNull()
    }

    fun addCmcCurrency(currency: CmcCurrency) {
        db.cmcCurrencies.add(currency)
    }

    fun addTradeCurrencies(exchId: Int, currencies: Set<String>) {
        log.debug("setTradePairs starts..")
        if (currencies.isEmpty()) return
        val pairs = currencies.map { currencyName ->
            TradeCurrency {
                exchangeId = exchId
                name = currencyName
            }
        }

        db.bulkInsert(TradeCurrencies) {
            pairs.forEach { pair ->
                item {
                    set(it.id, pair.id)
                    set(it.exchangeId, pair.exchangeId)
                    set(it.name, pair.name)
                }
            }
        }
        log.debug("setTradePairs ends")
    }
}

// для каждой валюты
interface Course: Entity<Course> {
    companion object : Entity.Factory<Course>()
    val id: Int
    var from: String
    var to: String
    var exchangeId: Int
    var price: Double
    var buy: Double
    var sell: Double
    var tag: String
}

fun Course.toDTO(): CourseDTO {
    return CourseDTO(
        id = id,
        from = from,
        to = to,
        buy = buy,
        sell = sell,
        price = price,
        tag = tag
    )
}

@Serializable
data class CourseDTO(
    val id: Int = 0,
    val from: String = "",
    val to: String = "",
    val buy: Double = 0.0,
    val sell: Double = 0.0,
    val price: Double = 0.0,
    val tag: String = ""
)

interface TradeCurrency: Entity<TradeCurrency> {
    companion object : Entity.Factory<TradeCurrency>()
    val id: Int
    var exchangeId: Int
    var name: String
}

interface CmcCurrency: Entity<CmcCurrency> {
    companion object : Entity.Factory<CmcCurrency>()
    val id: Int
    var cmcId: Int
    var name: String
}

typealias CourseDB = Course

data class PriceSearchResult(
    val spread: Double,
    val price: Double,
    val tag: String
)