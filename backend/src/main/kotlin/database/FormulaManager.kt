package com.github.perelshtein.database

import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.ValidationError
import com.github.perelshtein.exchanges.CrossCourses
import com.github.perelshtein.exchanges.FormulasGenerateAnswer
import com.github.perelshtein.exchanges.UpdateCourses
import com.github.perelshtein.routes.loadCourseFromFile
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.like
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.or
import org.ktorm.dsl.select
import org.ktorm.dsl.whereWithConditions
import org.ktorm.entity.Entity
import org.ktorm.entity.add
import org.ktorm.entity.filter
import org.ktorm.entity.find
import org.ktorm.entity.first
import org.ktorm.entity.firstOrNull
import org.ktorm.entity.forEach
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.update
import org.ktorm.schema.Table
import org.ktorm.schema.boolean
import org.ktorm.schema.double
import org.ktorm.schema.datetime
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.getValue
import kotlin.text.isEmpty
import kotlin.time.Duration

class FormulaManager: KoinComponent {
    object Formulas : Table<Formula>("formulas") {
        val id = int("id").primaryKey().bindTo { it.id }
        val from = varchar("currency_from").bindTo { it.from }
        val to = varchar("currency_to").bindTo { it.to }
        val tag = varchar("tag").bindTo { it.tag }
        val reserve = varchar("reserve").bindTo { it.reserve }
        val lastUpdated = datetime("last_updated").bindTo { it.lastUpdated }
        val isEnabled = boolean("is_enabled").bindTo { it.isEnabled }
        val price = double("price").bindTo { it.price }
        val spread = double("spread").bindTo { it.spread }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    private val log = LoggerFactory.getLogger("FormulaManager")
    val Database.formulas get() = this.sequenceOf(Formulas)

    fun getFormulas(start: Int = 0, count: Int = 100, isActive: Boolean? = null, filter: String? = null):
            Pair<Int, List<FormulaDTO>> {
        val query = db.from(Formulas)
            .select()
            .whereWithConditions {
                if (isActive != null) {
                    it += Formulas.isEnabled eq isActive
                }
                if (filter != null) {
                    it += (Formulas.from like "%$filter%") or (Formulas.to like "%$filter%") or (Formulas.tag like "%$filter%")
                }
            }

        val countFound = query.totalRecordsInAllPages
        if(countFound == 0) return 0 to emptyList()

        var result = query.limit(start, count)
            .map {
                val formula = Formulas.createEntity(it)
                if(formula.tag.startsWith("fromFile")) {
                    val priceFromFile = loadCourseFromFile(formula.from, formula.to).list.first().price
                    formula.toDTO().apply {
                        this.price = priceFromFile
                    }
                }
                else {
                    val p = recalcPrice(formula)
                    formula.toDTO().apply {
                        this.spread = p.first
                        this.price = p.second
                    }
                }
            }
        return countFound to result
    }

    suspend fun getFormula(from: String, to: String): FormulaDTO? {
        checkFromTo(from, to)
        val formula = db.formulas.firstOrNull { (it.from eq from) and (it.to eq to) } ?: return null

        if(!formula.isEnabled) {
            return formula.toDTO().apply {
                price = 0.0
                spread = 0.0
            }
        }
        return formula.toDTO()
    }

    // обновим цену для всех формул
    suspend fun updatePrice() {
        db.formulas.filter { it.isEnabled }.forEach { f ->
            val updatedPrice = recalcPrice(f)
            if(f.price > 0.0) {
                upsertFormula(f.apply {
                    price = updatedPrice.second
                    spread = updatedPrice.first
                })
            }
            else findNewPrice(f)
        }
    }

    suspend fun findNewPrice(formula: Formula) {
        require(formula.price == 0.0, { "Формула для ${formula.from} -> ${formula.to} уже найдена." })
        val cross: CrossCourses by inject()
        when(formula.reserve) {
            "off" -> {
                formula.isEnabled = false
                upsertFormula(formula)
                log.info("${formula.from} -> ${formula.to}: формула отключена. Действие при ошибке - Отключить.")
                return
            }

            "auto" -> {
                // не обновляем чаще чем раз в 10 сек!
                if(formula.lastUpdated.plusSeconds(10L) > LocalDateTime.now()) {
                    log.info("${formula.from} -> ${formula.to}: Пропускаю обновление: интервал в 10с")
                    return
                }

                cross.findCrossCourses(formula.from, formula.to).list.firstOrNull()?.let {
                    formula.tag = it.tag
                    upsertFormula(formula)
                    val updatedPrice = recalcPrice(formula)
                    formula.price = updatedPrice.second
                    formula.spread = updatedPrice.first
                    upsertFormula(formula)
                    log.info("${formula.from} -> ${formula.to}: найден новый курс ${it.tag}")
                    return
                }

                log.info("${formula.from} -> ${formula.to}: не удалось найти новый кросс-курс.")
                return
            }

            "CoinMarketCap" -> {
                // не обновляем чаще чем раз в минуту!
                if(formula.lastUpdated.plusMinutes(1L) > LocalDateTime.now()) {
                    log.info("${formula.from} -> ${formula.to}: Пропускаю обновление: интервал в 1мин")
                    return
                }

                val coursesUpdater: UpdateCourses by inject()
                val exchangeMgr: ExchangeManager by inject()
                val coinMarketCap = exchangeMgr.getExchangeByName("CoinMarketCap") ?:
                throw ValidationError("Ошибка загрузки настроек CoinMarketCap")
                if(!coinMarketCap.isEnabled) {
                    log.info("${formula.from} -> ${formula.to}: оставляем старый курс. CoinMarketCap отключен")
                    return
                }

                formula.tag = listOf("CoinMarketCap", formula.from, formula.to).joinToString("_")
                coursesUpdater.update(coinMarketCap.id) // найдем прямой и обратный курс
                cross.update() // перестроим весь граф, < 1с. это позволит использовать новый курс для поиска кросс-курсов

                val updatedPrice = recalcPrice(formula)
                formula.price = updatedPrice.second
                formula.spread = updatedPrice.first
                upsertFormula(formula)
                log.info("${formula.from} -> ${formula.to}: найден новый курс на CoinMarketCap")
            }
        }
    }

    private fun recalcPrice(formula: Formula): Pair<Double, Double> {
        val courseMgr: CourseManager by inject()
        var spread = 0.0
        var sum = 1.0
        formula.tag.split("*")
            .map { it.trim() }
            .forEach {
                val lastPrice = courseMgr.getPriceByTag(it)
                if(lastPrice != null) {
                    sum *= lastPrice.price
                    spread += lastPrice.spread
                }
                else sum = 0.0
            }
        return spread to sum
    }

    fun upsertFormula(formula: Formula) {
        val from = formula.from
        val to = formula.to
        checkFromTo(from, to)

        // и также цену сохраним сразу
        val updatedPrice = recalcPrice(formula)
        formula.spread = updatedPrice.first
        formula.price = updatedPrice.second
        formula.lastUpdated = LocalDateTime.now()

        // ищем существующую формулу
        db.formulas.find { (it.from eq from) and (it.to eq to) }?.let {
            //обновим формулу
            it.price = formula.price
            it.spread = formula.spread
            it.tag = formula.tag
            it.lastUpdated = formula.lastUpdated
            it.isEnabled = formula.isEnabled
            it.reserve = formula.reserve
            db.formulas.update(it)
            return
        }

        // формула не существует, добавим ее
        db.formulas.add(formula)
    }

    private fun checkFromTo(from: String, to: String) {
        if(from == to) throw ValidationError("Коды валют не могут совпадать")
        if(from.isEmpty() || to.isEmpty() ) throw ValidationError("Коды валют не могут быть пустыми")
    }

    fun getFormulaName(id: Int): String? {
        db.formulas.find { it.id eq id }?.let {
            return "${it.from} -> ${it.to}"
        }
        return null
    }

    fun deleteFormula(id: Int) {
        val name = getFormulaName(id)
        db.delete(Formulas) { it.id eq id }
        log.info("Удалена формула $name")
    }
}

interface Formula: Entity<Formula> {
    companion object : Entity.Factory<Formula>()
    var id: Int
    var from: String
    var to: String
    var tag: String
    var reserve: String
    var lastUpdated: LocalDateTime
    var isEnabled: Boolean
    var price: Double
    var spread: Double
}

@Serializable
data class FormulaDTO(
    val id: Int = 0,
    var from: String,
    var to: String,
    var tag: String,
    var reserve: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    var lastUpdated: LocalDateTime = LocalDateTime.now(),
    var isEnabled: Boolean,
    var price: Double = 0.0,
    var spread: Double = 0.0
)

fun Formula.toDTO() = FormulaDTO(id, from, to, tag, reserve, lastUpdated, isEnabled, price, spread)

fun FormulaDTO.toDB() = Formula {
    id = this@toDB.id
    from = this@toDB.from
    to = this@toDB.to
    tag = this@toDB.tag
    reserve = this@toDB.reserve
    lastUpdated = this@toDB.lastUpdated
    isEnabled = this@toDB.isEnabled
    price = this@toDB.price
    spread = this@toDB.spread
}