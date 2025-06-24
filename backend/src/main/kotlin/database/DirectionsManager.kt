package com.github.perelshtein.database

import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.OptionsManager
import com.github.perelshtein.ReserveFormatter
import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.DirectionShortDTO.Currency
import com.github.perelshtein.exchanges.Autopay
import com.github.perelshtein.exchanges.PayInAPI
import com.github.perelshtein.exchanges.PayOutAPI
import com.github.perelshtein.routes.ConvertPrice
import com.github.perelshtein.routes.Item
import com.github.perelshtein.routes.Rates
import com.github.perelshtein.routes.StatusGroupDTO
import com.github.perelshtein.routes.UserCurrencyFieldDTO
import com.github.perelshtein.routes.directions
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.select
import org.ktorm.dsl.inList
import org.ktorm.dsl.insertAndGenerateKey
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.mapNotNull
import org.ktorm.dsl.or
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import org.ktorm.dsl.whereWithConditions
import org.ktorm.entity.Entity
import org.ktorm.entity.add
import org.ktorm.entity.any
import org.ktorm.entity.filter
import org.ktorm.entity.find
import org.ktorm.entity.first
import org.ktorm.entity.firstOrNull
import org.ktorm.entity.isEmpty
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.toList
import org.ktorm.entity.update
import org.ktorm.schema.Table
import org.ktorm.schema.boolean
import org.ktorm.schema.datetime
import org.ktorm.schema.double
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.and
import kotlin.collections.firstOrNull
import kotlin.getValue
import kotlin.math.max
import kotlin.math.min

class DirectionsManager: KoinComponent {
    object Directions : Table<Direction>("directions") {
        val id = int("id").primaryKey().bindTo { it.id }
        val fromId = int("currency_from_id").bindTo { it.fromId }
        val toId = int("currency_to_id").bindTo { it.toId }
        val isActive = boolean("is_active").bindTo { it.isActive }
        val isExport = boolean("is_export").bindTo { it.isExport }
        val minSum = double("min_sum").bindTo { it.minSum }
        val minSumCurrencyId = int("min_sum_currency_id").bindTo { it.minSumCurrencyId }
        val maxSum = double("max_sum").bindTo { it.maxSum }
        val maxSumCurrencyId = int("max_sum_currency_id").bindTo { it.maxSumCurrencyId }
        val profit = double("profit").bindTo { it.profit }
        val formula = int("formula_id").references(FormulaManager.Formulas) { it.formula }
        val statusTemplate = int("status_id").references(StatusTemplates) { it.statusTemplate }
    }

    object StatusTemplates : Table<StatusTemplate>("status_templates") {
        val id = int("id").primaryKey().bindTo { it.id }
        val caption = varchar("caption").bindTo { it.caption }
        val lastUpdated = datetime("last_updated").bindTo { it.lastUpdated }
    }

    object Statuses : Table<Status>("statuses") {
        val id = int("id").primaryKey().bindTo { it.id }
        val templateId = int("template_id").references(StatusTemplates) { it.template }
        val statusType = varchar("status_type").bindTo { it.statusType }
        val text = varchar("text").bindTo { it.text }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val log = LoggerFactory.getLogger("DirectionsManager")
    val currencyMgr by inject<CurrencyManager>()
    val Database.directions get() = this.sequenceOf(Directions)
    val Database.statusTemplates get() = this.sequenceOf(StatusTemplates)
    val Database.statuses get() = this.sequenceOf(Statuses)

    init {
        if(db.statusTemplates.isEmpty()) {
            upsertStatuses(StatusGroupDTO(
                name="Default",
                idsFrom = emptySet(),
                idsTo = emptySet(),
                list = listOf(
                    StatusGroupDTO.Status("popup", "Сайт работает в тестовом режиме."),
                    StatusGroupDTO.Status("deadlines", "Обработка заявки занимает до 30мин."),
                    StatusGroupDTO.Status("new", "Заявка создана.\nЗдесь будут реквизиты"),
                    StatusGroupDTO.Status("waitingForPayment", "Ожидаем оплату заявки"),
                    StatusGroupDTO.Status("waitingForConfirmation", "Ожидаем подтверждения оплаты"),
                    StatusGroupDTO.Status("payed", "Заявка оплачена.\nМы получили средства и готовы отправить вам исходящий перевод."),
                    StatusGroupDTO.Status("waitingForPayout", "Перевод отправлен. Ждем, когда транзакция будет подтверждена."),
                    StatusGroupDTO.Status("onCheck", "Заявка находится на проверке оператором."),
                    StatusGroupDTO.Status("completed", "Заявка успешно выполнена. Спасибо, что воспользовались нашим сервисом!"),
                    StatusGroupDTO.Status("cancelled", "Заявка отменена."),
                    StatusGroupDTO.Status("error", "Ошибка. Напишите в техподдержку"),
                    StatusGroupDTO.Status("deleted", "Заявка удалена."),
                    StatusGroupDTO.Status("instructions", "Для обмена нужно выполнить несколько шагов")
                )
            ))
        }
    }

    fun getDirByXmlCode(from: String, to: String): Int {
        val fromId = currencyMgr.getCurrencyByXmlCode(from)?.id
        val toId = currencyMgr.getCurrencyByXmlCode(to)?.id
        if(fromId == null || toId == null) return db.directions.first().id

        db.directions.find { (it.fromId eq fromId) and (it.toId eq toId) }?.let { return it.id }
        db.directions.find { (it.fromId eq fromId) }?.let { return it.id }
        db.directions.find { (it.toId eq toId) }?.let { return it.id }
        return db.directions.first().id
    }

    suspend fun getDirections(
        start: Int? = 0,
        count: Int? = 100,
        isActive: Boolean? = null,
        filter: String? = null,
        fromId: Int? = null,
        toId: Int? = null
    ):
            Pair<Int, List<DirectionShortDTO>> {
        val formulaMgr by inject<FormulaManager>()
        //val activeCurrencies = currencyMgr.getCurrencies().filter { it.isEnabled }.map { it.id }
        val query = db.from(Directions)
            .select()
            .whereWithConditions {
                if (isActive != null) {
                    it += (Directions.isActive eq isActive)
//                    it += (Directions.fromId inList activeCurrencies)
//                    it += (Directions.toId inList activeCurrencies)
                }
                if (fromId != null) {
                    it += (Directions.fromId eq fromId)
                }
                if (toId != null) {
                    it += (Directions.toId eq toId)
                }
                if (filter != null) {
                    val codes = currencyMgr.getCurrencies()
                        .map { it.code to it.id }
                        .filter { it.first.contains(filter, ignoreCase = true) }
                        .map { it.second }
                        .toSet()
                    if (codes.isNotEmpty()) {
                        it += ((Directions.fromId inList codes) or (Directions.toId inList codes))
                    } else {
                        // When codes is empty, return no results
                        it += (Directions.id eq -1) // Assuming id is never -1
                    }
                }
            }

        val countFound = query.totalRecordsInAllPages
        if (countFound == 0) return 0 to emptyList()

        val result = query.limit(start, count)
            .map {
                Directions.createEntity(it)
            }

        //Загрузим краткую инф о валютах
        val currencyIds = (result.map { it.fromId } + result.map { it.toId }).toSet()
        val currencyMap = currencyMgr.getCurrenciesByIds(currencyIds)
            .associateBy { it.id }

        // преобразуем id каждой валюты в id, name, code
        return countFound to result.map {
            val from = currencyMap[it.fromId]!!
            val to = currencyMap[it.toId]!!
            var lastPrice = 0.0
            formulaMgr.getFormula(from.code, to.code)?.let { f ->
                lastPrice = f.price * (1 - it.profit / 100)
            }
            DirectionShortDTO(
                it.id,
                Currency(from.id, from.name, from.code, from.fidelity),
                Currency(to.id, to.name, to.code, to.fidelity),
                it.isActive,
                lastPrice
            )
        }
    }

    fun getDirectionNames(ids: List<Int>): List<String> {
        return db.from(Directions)
            .select(Directions.fromId, Directions.toId)
            .where { Directions.id inList ids }
            .map {
                var from = "N/A"
                var to = "N/A"
                it[Directions.fromId]?.let { from = currencyMgr.getCurrencyById(it)?.name ?: "N/A" }
                it[Directions.toId]?.let { to = currencyMgr.getCurrencyById(it)?.name ?: "N/A" }
                "${from} -> ${to}"
            }
    }

    fun getCurrenciesGiveForTemplateId(id: Int): Set<Int>? {
        return db.from(Directions)
            .select(Directions.fromId, Directions.statusTemplate)
            .where { Directions.statusTemplate eq id }
            .mapNotNull { it[Directions.fromId] }
            .toSet()
    }

    fun getCurrenciesGetForTemplateId(id: Int): Set<Int>? {
        return db.from(Directions)
            .select(Directions.toId, Directions.statusTemplate)
            .where { Directions.statusTemplate eq id }
            .mapNotNull { it[Directions.toId] }
            .toSet()
    }

    //массовое редактирование
    fun upsertDirections(ids: List<Int>, isEnable: Boolean? = null, profit: Double? = null, minSum: Double? = null,
         minSumCurrency: Int? = null,  maxSum: Double? = null, maxSumCurrency: Int? = null) {
//        if(listOf(isEnable, profit, minSum, minSumCurrency, maxSum, maxSumCurrency).any { it == null }) {
//            return
//        }
        if(ids.isEmpty()) return

        db.update(Directions) { dir ->
            isEnable?.let { set(dir.isActive, it) }
            profit?.let { set(dir.profit, it) }
            minSum?.let { set(dir.minSum, it) }
            minSumCurrency?.let { set(dir.minSumCurrencyId, it) }
            maxSum?.let { set(dir.maxSum, it) }
            maxSumCurrency?.let { set(dir.maxSumCurrencyId, it) }
            where { Directions.id inList ids }
        }
    }

    fun upsertDirection(direction: DirectionDTO) {
        if(direction.id == 0) {
            if(db.directions.any { (it.fromId eq direction.fromId) and (it.toId eq direction.toId) }) {
                val currencyMgr: CurrencyManager by inject()
                val give = currencyMgr.getCurrencyById(direction.fromId) ?: throw ValidationError("Валюта Отдаю (id=${direction.fromId}) не найдена")
                val get = currencyMgr.getCurrencyById(direction.toId) ?: throw ValidationError("Валюта Получаю (id=${direction.toId}) не найдена")
                throw ValidationError("Направление ${give.name} -> ${get.name} уже существует")
            }
            db.directions.add(direction.toDB())
        }
        else db.directions.update(direction.toDB())
    }

    fun getDirection(id: Int): Direction? {
        return db.directions.find { it.id eq id }
    }

    fun deleteDirection(id: Int) {
        db.delete(Directions) { it.id eq id }
    }

    fun getStatusTemplates(): List<StatusTemplate> {
        return db.statusTemplates.toList()
    }

    fun deleteStatusTemplate(id: Int) {
        db.delete(StatusTemplates) { it.id eq id }
    }

    fun getStatuses(groupId: Int): List<Status> {
        return db.statuses
            .filter { it.templateId eq groupId }
            .toList()
    }

    fun upsertStatuses(st: StatusGroupDTO) {
        var id = st.id
        if(id == 0) {
            if(db.statusTemplates.any { it.caption eq st.name }) {
                throw ValidationError("Группа статусов с таким названием уже существует")
            }
            id = db.insertAndGenerateKey(StatusTemplates) {
                set(it.caption, st.name)
                set(it.lastUpdated, LocalDateTime.now())
            } as Int

            st.list.forEach { e ->
                db.statuses.add(Status {
                    template = StatusTemplate { this.id = id }
                    statusType = e.statusType
                    text = e.text
                })
            }
        }
        else {
            val template = db.statusTemplates.find { it.id eq id }
            if(template == null) throw ValidationError("Группа статусов \"${st.name}\" (id=$id) не существует")
            db.statusTemplates.update(template.apply {
                caption = st.name
                lastUpdated = LocalDateTime.now()
            })

            st.list.forEach { e ->
                val old = db.statuses.find { it.templateId eq id and (it.statusType eq e.statusType) }
                if(old == null) throw ValidationError("Статус ${e.statusType} не существует")
                db.statuses.update(old.apply {
                    text = e.text
                })
            }
        }

        if(st.idsFrom.isNotEmpty() && st.idsTo.isNotEmpty()) {
            db.update(Directions) {
                set(it.statusTemplate, id)
                where { (Directions.fromId inList st.idsFrom) and (Directions.toId inList st.idsTo) }
            }
        }
    }

    suspend fun getDirectionsXML(): Rates {
        val optionsMgr: OptionsManager by inject()
        val formulaMgr: FormulaManager by inject()
        val converter: ConvertPrice by inject()
        val reserveFmt: ReserveFormatter by inject()
        if(!optionsMgr.getOptions().isExportCourses) {
            return Rates(emptyList())
        }

        return Rates(
            db.from(Directions)
                .select()
                .where { (Directions.isExport eq true) and (Directions.isActive eq true) }
                .map {
                    val dir = Directions.createEntity(it)
                    val from = currencyMgr.getCurrencyById(dir.fromId) ?: throw ValidationError("Валюта Отдаю не найдена")
                    val to = currencyMgr.getCurrencyById(dir.toId) ?: throw ValidationError("Валюта Получаю не найдена")
                    val minSumCurrency = currencyMgr.getCurrencyById(dir.minSumCurrencyId)?.code
                        ?: throw ValidationError("Валюта для минимальной суммы не найдена")
                    val maxSumCurrency = currencyMgr.getCurrencyById(dir.maxSumCurrencyId)?.code
                        ?: throw ValidationError("Валюта для максимальной суммы не найдена")
                    var lastPrice = 0.0
                    formulaMgr.getFormula(from.code, to.code)?.let { f ->
                        lastPrice = f.price * (1 - dir.profit / 100)
                    }

                    var reserve = 0.0
                    currencyMgr.getReserve(to.code)?.let {
                        val answer = reserveFmt.calcReserve(it)
                        if(answer.reserveType == "off") {
                            reserve = converter.convertAmount(10000.0, "USDT", to.code)
                        }
                        else {
                            reserve = converter.convertAmount(answer.value!!, answer.reserveCurrency!!, to.code)
                        }
                    }

                    Item(from = from.xmlCode,
                        to = to.xmlCode,
                        giveCourse = 1.0,
                        getCourse = lastPrice,
                        amount = reserve, //резерв в валюте Получаю
                        minAmount = converter.convertAmount(dir.minSum, minSumCurrency, from.code), //мин сумма Отдаю
                        maxAmount = converter.convertAmount(dir.maxSum, maxSumCurrency, from.code), //макс сумма Отдаю
                        param = if(from.payin == "manual" || to.payout == "manual") "manual" else null
                    )
                }.filter {
                    it.from.isNotEmpty() && it.to.isNotEmpty() && it.amount > 0.0
                }

        )
    }
}

interface Direction: Entity<Direction> {
    companion object : Entity.Factory<Direction>()
    var id: Int
    var fromId: Int
    var toId: Int
    var isActive: Boolean
    var isExport: Boolean
    var minSum: Double
    var minSumCurrencyId: Int
    var maxSum: Double
    var maxSumCurrencyId: Int
    var profit: Double
    var formula: Formula
    var statusTemplate: StatusTemplate
}

fun Direction.toDTO(): DirectionDTO {
    return DirectionDTO(
        id = this.id,
        fromId = this.fromId,
        toId = this.toId,
        isActive = this.isActive,
        isExport = this.isExport,
        minSum = this.minSum,
        minSumCurrencyId = this.minSumCurrencyId,
        maxSum = this.maxSum,
        maxSumCurrencyId = this.maxSumCurrencyId,
        profit = this.profit,
        formulaId = this.formula.id,
        statusId = this.statusTemplate.id
    )
}

fun DirectionDTO.toDB(): Direction {
    return Direction {
        id = this@toDB.id
        fromId = this@toDB.fromId
        toId = this@toDB.toId
        isActive = this@toDB.isActive
        isExport = this@toDB.isExport
        minSum = this@toDB.minSum
        minSumCurrencyId = this@toDB.minSumCurrencyId
        maxSum = this@toDB.maxSum
        maxSumCurrencyId = this@toDB.maxSumCurrencyId
        profit = this@toDB.profit
        formula = Formula { id = this@toDB.formulaId }
        statusTemplate = StatusTemplate { id = this@toDB.statusId }
    }
}

@Serializable
data class DirectionDTO(
    var id: Int,
    var fromId: Int,
    var toId: Int,
    var isActive: Boolean,
    var isExport: Boolean,
    var minSum: Double,
    var minSumCurrencyId: Int,
    var maxSum: Double,
    var maxSumCurrencyId: Int,
    var profit: Double,
    var formulaId: Int,
    var statusId: Int
)

@Serializable
data class DirectionUserDTO(
    var fromId: Int,
    var toId: Int,
    var minSumGive: Double, //эти значения пересчитаны в валюту Отдаю
    var maxSumGive: Double,
    var minSumGet: Double, //эти значения пересчитаны в валюту Получаю
    var maxSumGet: Double,
    var statusId: Int,
    var popup: String,
    var price: Double,
    var giveFields: List<UserCurrencyFieldDTO>,
    var getFields: List<UserCurrencyFieldDTO>
)

@Serializable
data class DirectionShortDTO(
    val id: Int,
    val from: Currency,
    val to: Currency,
    val isActive: Boolean,
    val price: Double
) {
    @Serializable
    data class Currency(
        val id: Int,
        val name: String,
        val code: String,
        val fidelity: Int
    )
}

interface StatusTemplate: Entity<StatusTemplate> {
    companion object : Entity.Factory<StatusTemplate>()
    var id: Int
    var caption: String
    var lastUpdated: LocalDateTime
}

interface Status: Entity<Status> {
    companion object : Entity.Factory<Status>()
    var id: Int
    var template: StatusTemplate
    var statusType: String
    var text: String
}