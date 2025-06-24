package com.github.perelshtein.database

import com.github.perelshtein.AccessControl
import com.github.perelshtein.AccessControl.Users
import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.NotifySender
import com.github.perelshtein.ValidationError
import com.github.perelshtein.exchanges.Autopay
import com.github.perelshtein.roundup
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.delete
import org.ktorm.dsl.desc
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.greaterEq
import org.ktorm.dsl.innerJoin
import org.ktorm.dsl.lessEq
import org.ktorm.dsl.like
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.or
import org.ktorm.dsl.orderBy
import org.ktorm.dsl.select
import org.ktorm.dsl.whereWithConditions
import org.ktorm.entity.Entity
import org.ktorm.entity.add
import org.ktorm.entity.count
import org.ktorm.entity.firstOrNull
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.update
import org.ktorm.schema.Table
import org.ktorm.schema.boolean
import org.ktorm.schema.datetime
import org.ktorm.schema.double
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.String
import kotlin.collections.firstOrNull
import kotlin.getValue

class OrdersManager: KoinComponent {
    object Orders: Table<OrdersRecord>("orders") {
        val id = int("id").primaryKey().bindTo { it.id }
        val userId = int("user_id").bindTo { it.userId }
        val dateCreated = datetime("date_created").bindTo { it.dateCreated }
        val dateUpdated = datetime("date_updated").bindTo { it.dateUpdated }
        val walletFrom = varchar("wallet_from").bindTo { it.walletFrom }
        val walletTo = varchar("wallet_to").bindTo { it.walletTo }
        val requisites = varchar("requisites").bindTo { it.requisites }
        val amountFrom = double("amount_from").bindTo { it.amountFrom } // количество валюты А
        val amountTo = double("amount_to").bindTo { it.amountTo } // количество валюты Б
        val profit = double("profit").bindTo { it.profit }
        val status = varchar("status").bindTo { it.status }
        val isActive = boolean("is_active").bindTo { it.isActive }
        val fromCode = varchar("from_code").bindTo { it.fromCode }
        val fromXmlCode = varchar("from_xml_code").bindTo { it.fromXmlCode }
        val fromName = varchar("from_name").bindTo { it.fromName }
        val toCode = varchar("to_code").bindTo { it.toCode }
        val toXmlCode = varchar("to_xml_code").bindTo { it.toXmlCode }
        val toName = varchar("to_name").bindTo { it.toName }
        val fieldsGive = varchar("fields_give").bindTo { it.fieldsGive }
        val fieldsGet = varchar("fields_get").bindTo { it.fieldsGet }
        val rateFrom = double("rate_from").bindTo { it.rateFrom }
        val rateTo = double("rate_to").bindTo { it.rateTo }
        val isManualReceive = boolean("is_manual_receive").bindTo { it.isManualReceive }
        val isManualSend = boolean("is_manual_send").bindTo { it.isManualSend }
        val payinFee = double("payin_fee").bindTo { it.payinFee }
        val payoutFee = double("payout_fee").bindTo { it.payoutFee }
        val isNeedsTxId = boolean("is_needs_tx_id").bindTo { it.isNeedsTxId }
        val dateStatusUpdated = datetime("date_status_updated").bindTo { it.dateStatusUpdated }
        val statusHistory = varchar("status_history").bindTo { it.statusHistory }
        val refId = int("ref_id").bindTo { it.refId }
        val orderValue = double("order_value").bindTo { it.orderValue }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val log = LoggerFactory.getLogger("OrdersManager")
    val Database.orders get() = this.sequenceOf(Orders)

    fun getActiveOrder(userId: Int): OrdersRecord? {
        return db.orders.firstOrNull { (it.userId eq userId) and (it.isActive eq true) }
    }

    fun getCountCompleted(userId: Int): Int {
        return db.orders.count { (it.userId eq userId) and (it.status eq "completed") }
    }

    fun getOrders(
        start: Int? = 0,
        count: Int? = 100,
        status: String? = null,
        filter: String? = null,
        userId: Int? = null,
        fromXmlCode: String? = null,
        toXmlCode: String? = null,
        dateStart: LocalDateTime? = null,
        dateEnd: LocalDateTime? = null
    ): Pair<Int, List<OrdersRecord>> {
        val query = db.from(Orders)
            .let { query ->
                if (fromXmlCode != null || toXmlCode != null || filter != null) {
                    query.innerJoin(Users, on = Users.id eq Orders.userId)
                } else {
                    query
                }
            }
            .select()
            .orderBy(Orders.dateCreated.desc())
            .whereWithConditions {
                if(status != null) {
                    it += Orders.status eq status
                }
                if(filter != null) {
                    it += (Users.name like "%$filter%") or (Users.mail like "%$filter%") or (Orders.requisites like "%$filter%") or
                        (Orders.walletFrom like "%$filter%") or (Orders.walletTo like "%$filter%")
                }
                if(userId != null) {
                    it += Orders.userId eq userId
                }
                if(fromXmlCode != null) {
                    it += Orders.fromXmlCode eq fromXmlCode
                }
                if(toXmlCode != null) {
                    it += Orders.toXmlCode eq toXmlCode
                }
                if(dateStart != null) {
                    it += Orders.dateCreated greaterEq dateStart
                }
                if(dateEnd != null) {
                    it += Orders.dateCreated lessEq dateEnd
                }
            }

        val countFound = query.totalRecordsInAllPages
        if (countFound == 0) return 0 to emptyList()

        val result = query
            .limit(start, count)
            .map { Orders.createEntity(it) }
        return countFound to result
    }

    fun getOrder(id: Int): OrdersRecord? {
        return db.orders.firstOrNull { it.id eq id }
    }

    suspend fun upsertOrder(src: ORDER_SRC, order: OrdersRecord) {
        val notifySender: NotifySender by inject()
        val now = LocalDateTime.now()
        var isNeedNotify = false
        val referralManager: ReferralManager by inject()
        val cashbackManager: CashbackManager by inject()
        val currencyMgr: CurrencyManager by inject()
        val directionsManager: DirectionsManager = KoinJavaComponent.get(DirectionsManager::class.java)

        // после создания заявки можно менять только эти поля, чтобы избежать мошенничества
        db.orders.firstOrNull { it.id eq order.id }?.let {
            if(order.status != it.status) {
                it.dateStatusUpdated = now
                val oldStatusList = it.toAdminDTO().statusHistory
                it.statusHistory = Json.encodeToString(oldStatusList + listOf(OrderHistoryDTO(date = now, src = src.value, status = order.status)))
                isNeedNotify = true
            }
            it.status = order.status
            it.isActive = if(order.status == "cancelled" || order.status == "deleted" || order.status == "completed" ||
                order.status == "error" || order.status == "cancelledUnprofitable") false else true
            it.requisites = order.requisites
            it.dateUpdated = now
            it.walletFrom = order.walletFrom

            it.rateFrom = order.rateFrom
            it.rateTo = order.rateTo
            it.payinFee = order.payinFee
            it.payoutFee = order.payoutFee
            it.profit = order.profit
            it.isNeedsTxId = order.isNeedsTxId
            it.orderValue = order.orderValue

            if(it.status == "completed") {
                // если отдаю и Получаю вручную, посчитаем стоимость ордера
                if(it.isManualReceive && it.isManualSend) {
                    val from = currencyMgr.getCurrencyByXmlCode(it.fromXmlCode) ?: throw ValidationError("Валюта Отдаю не найдена")
                    val to = currencyMgr.getCurrencyByXmlCode(it.toXmlCode) ?: throw ValidationError("Валюта Получаю не найдена")
                    val usdt = currencyMgr.getCurrencyByCode("USDT") ?: throw ValidationError("Валюта USDT не найдена")
                    it.orderValue = (it.rateFrom * it.amountFrom).roundup(usdt.fidelity)
                    directionsManager.getDirections(fromId = from.id, toId = to.id, count = 1).let { d ->
                        d.second.firstOrNull()?.let { s ->
                            directionsManager.getDirection(s.id)?.let { dir ->
                                it.profit = it.orderValue * dir.profit / 100
                            }
                        }
                    }
                }
                referralManager.calcBonus(it)
                cashbackManager.calcBonus(it)
            }

            db.orders.update(it)
            if(isNeedNotify) {
                notifySender.send(order)
            }
            return
        }

        order.dateCreated = now
        order.dateUpdated = now
        order.dateStatusUpdated = now
        order.statusHistory = Json.encodeToString(listOf(OrderHistoryDTO(date = now, src = src.value, status = order.status)))
        db.orders.add(order)
//        notifySender.send(order)
    }
}

interface OrdersRecord: Entity<OrdersRecord> {
    companion object: Entity.Factory<OrdersRecord>()
    var id: Int
    var userId: Int
    var dateCreated: LocalDateTime
    var dateUpdated: LocalDateTime
    var walletFrom: String
    var walletTo: String
    var requisites: String
    var amountFrom: Double
    var amountTo: Double
    var profit: Double
    var status: String
    var isActive: Boolean
    var fromCode: String
    var fromXmlCode: String
    var fromName: String
    var toCode: String
    var toXmlCode: String
    var toName: String
    var fieldsGive: String?
    var fieldsGet: String?
    var rateFrom: Double
    var rateTo: Double
    var isManualReceive: Boolean
    var isManualSend: Boolean
    var payinFee: Double
    var payoutFee: Double
    var isNeedsTxId: Boolean
    var dateStatusUpdated: LocalDateTime
    var statusHistory: String?
    var refId: Int?
    var orderValue: Double
}

fun OrdersRecord.getTimeInterval(): Int {
    return when (status) {
        "waitingForPayment" -> if (isManualReceive) 15 else 30 // ручной прием - 15мин, авто - 30мин
        "waitingForConfirmation" -> if (isManualReceive) 60 else 720 // 12h для автоприема (низкая комиссия btc - долго ждать)
        "waitingForPayout" -> 60
        "payed" -> if (isManualReceive) 30 else 3
        "error" -> 1
        else -> 30
    }
}

@Serializable
data class OrderHistoryDTO(
    @Serializable(LocalDateTimeSerializer::class)
    val date: LocalDateTime,
    val src: String,
    val status: String
)

@Serializable
data class OrderUserDTO(
    val id: Int,
    val userId: Int,
    @Serializable(LocalDateTimeSerializer::class)
    val dateCreated: LocalDateTime,
    @Serializable(LocalDateTimeSerializer::class)
    val dateUpdated: LocalDateTime,
    val walletFrom: String,
    val walletTo: String,
    val requisites: String,
    val amountFrom: Double,
    val amountTo: Double,
    val status: String,
    val isActive: Boolean,
    val fromXmlCode: String,
    val fromCode: String,
    val fromName: String,
    val toXmlCode: String,
    val toCode: String,
    val toName: String,
    val fieldsGive: Map<String, String>,
    val fieldsGet: Map<String, String>,
    var needsTxId: Boolean = false,
    @Serializable(LocalDateTimeSerializer::class)
    val dateStatusUpdated: LocalDateTime,
    var deleteInterval: Int = 30, // после последнего обновления статуса через сколько мин удалять заявку
)

@Serializable
data class OrderAdminDTO(
    val id: Int,
    val userId: Int,
    @Serializable(LocalDateTimeSerializer::class)
    val dateCreated: LocalDateTime,
    @Serializable(LocalDateTimeSerializer::class)
    val dateUpdated: LocalDateTime,
    val walletFrom: String,
    val walletTo: String,
    val requisites: String,
    val amountFrom: Double,
    val amountTo: Double,
    val profit: Double,
    val status: String,
    val isActive: Boolean,
    val fromXmlCode: String,
    val toXmlCode: String,
    val fieldsGive: Map<String, String>,
    val fieldsGet: Map<String, String>,
    val isManualGive: Boolean = false,
    val isManualGet: Boolean = false,
    val rateGive: Double,
    val rateGet: Double,
    val payinFee: Double,
    val payoutFee: Double,
    @Serializable(LocalDateTimeSerializer::class)
    val dateStatusUpdated: LocalDateTime,
    val statusHistory: List<OrderHistoryDTO>,
    val refId: Int? = null
)

fun OrdersRecord.toUserDTO() = OrderUserDTO(
    id, userId, dateCreated, dateUpdated, walletFrom, walletTo, requisites,
    amountFrom, amountTo, status, isActive, fromXmlCode, fromCode, fromName,
    toXmlCode, toCode, toName,
    fieldsGive?.let { Json.decodeFromString(it) } ?: emptyMap(),
    fieldsGet?.let { Json.decodeFromString(it) } ?: emptyMap(),
    isNeedsTxId, dateStatusUpdated
)

fun OrdersRecord.toAdminDTO() = OrderAdminDTO(
    id, userId, dateCreated, dateUpdated, walletFrom, walletTo, requisites,
    amountFrom, amountTo, profit, status, isActive, fromXmlCode, toXmlCode,
    fieldsGive?.let { Json.decodeFromString(it) } ?: emptyMap(),
    fieldsGet?.let { Json.decodeFromString(it) } ?: emptyMap(),
    isManualReceive, isManualSend, rateFrom, rateTo, payinFee, payoutFee,
    dateStatusUpdated,
    statusHistory?.let { Json.decodeFromString<List<OrderHistoryDTO>>(it) } ?: emptyList(),
    refId
)

enum class ORDER_SRC(val value: String) {
    ADMIN_PANEL("admin_panel"),
    AUTOPAY("autopay"),
    USER("user")
}
