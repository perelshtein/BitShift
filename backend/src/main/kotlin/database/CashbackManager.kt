package com.github.perelshtein.database

import com.github.perelshtein.AccessControl
import com.github.perelshtein.BONUS_TYPE
import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.OptionsManager
import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.ReferralManager.PayoutRequests
import com.github.perelshtein.database.ReferralManager.PayoutRequests.bindTo
import com.github.perelshtein.database.ReferralManager.Referrals
import com.github.perelshtein.database.ReferralManager.Referrals.bindTo
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.greaterEq
import org.ktorm.dsl.innerJoin
import org.ktorm.dsl.lessEq
import org.ktorm.dsl.like
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.sum
import org.ktorm.dsl.update
import org.ktorm.dsl.whereWithConditions
import org.ktorm.entity.Entity
import org.ktorm.entity.add
import org.ktorm.entity.aggregateColumns
import org.ktorm.entity.any
import org.ktorm.entity.filter
import org.ktorm.entity.firstOrNull
import org.ktorm.entity.sequenceOf
import org.ktorm.schema.Table
import org.ktorm.schema.boolean
import org.ktorm.schema.datetime
import org.ktorm.schema.double
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.getValue

class CashbackManager: KoinComponent {
    object Cashback: Table<CashbackRecord>("cashback_earnings") {
        val id = int("id").primaryKey().bindTo { it.id }
        val userId = int("user_id").bindTo { it.userId }
        val orderId = int("order_id").bindTo { it.orderId }
        val earnings = double("earnings").bindTo { it.earnings }
        val isPaidOut = boolean("is_paid_out").bindTo { it.isPaidOut }
        val dateCreated = datetime("date_created").bindTo { it.dateCreated }
        val cashbackRequestId = int("cashback_request_id").bindTo { it.cashbackRequestId }
    }

    object CashbackRequests: Table<CashbackRequestRecord>("cashback_requests") {
        val id = int("id").primaryKey().bindTo { it.id }
        val userId = int("user_id").bindTo { it.userId }
        val dateCreated = datetime("date_created").bindTo { it.dateCreated }
        val amount = double("amount").bindTo { it.amount }
        val status = varchar("status").bindTo { it.status }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val log = LoggerFactory.getLogger("CashbackManager")
    val Database.cashback get() = this.sequenceOf(Cashback)
    val Database.cashbackRequests get() = this.sequenceOf(CashbackRequests)

    // считаем бонус для каждой заявки со статусом "Выполнено" только один раз
    fun calcBonus(order: OrdersRecord) {
        val accessControl: AccessControl by inject()
        val optionsManager: OptionsManager by inject()
        val userProfile = accessControl.getUserById(order.userId)
        if(userProfile == null) {
            log.error("Пользователь с id=${order.userId} не найден")
            return
        }
        if(db.cashback.any { (it.orderId eq order.id) }) {
            log.warn("Кэшбек по заявке ${order.id} уже начислен. Пользователь ${userProfile.name}")
            return
        }

        val percent = userProfile.cashbackPercent ?: optionsManager.getOptions().cashbackPercent
        if(BONUS_TYPE.fromValue(userProfile.cashbackType) == BONUS_TYPE.FROM_PROFIT) {
            val bonus = order.profit * percent / 100
            if(bonus == 0.0) {
                log.info("Кэшбек по заявке ${order.id} не начислен. Пользователь ${userProfile.name}")
                return
            }
            db.cashback.add(CashbackRecord {
                this.userId = order.userId
                this.orderId = order.id
                this.earnings = bonus
                this.dateCreated = LocalDateTime.now()
            })
            log.info("Пользователю ${userProfile.name} начислен кэшбек ${bonus} USDT")
        }
        else {
            val bonus = order.orderValue * percent / 100
            if(bonus == 0.0) {
                log.info("Кэшбек по заявке ${order.id} не начислен. Пользователь ${userProfile.name}")
                return
            }
            db.cashback.add(CashbackRecord {
                this.userId = order.userId
                this.orderId = order.id
                this.earnings = bonus
                this.dateCreated = LocalDateTime.now()
            })
            log.info("Пользователю ${userProfile.name} начислен кэшбек ${bonus} USDT")
        }
    }

    fun createPayout(userId: Int): PayoutInfo {
        val sum = db.cashback
            .filter { (it.userId eq userId) and (it.isPaidOut eq false) }
            .aggregateColumns { sum(it.earnings) }
        if(sum == null) throw ValidationError("Сумма к выплате = 0")

        val payout = CashbackRequestRecord {
            this.userId = userId
            this.dateCreated = LocalDateTime.now()
            this.amount = sum
        }
        db.cashbackRequests.add(payout) //generating ID

        db.update(Cashback) {
            set(it.cashbackRequestId, payout.id)
            where { (it.userId eq userId) and (it.isPaidOut eq false) }
        }
        payout.flushChanges()
        return PayoutInfo(payout.id, sum, payout.dateCreated)
    }

    fun finishPayout(userId: Int): PayoutInfo {
        val payout = getActivePayout(userId) ?: throw ValidationError("Активный запрос на выплату не найден")
        db.update(Cashback) {
            set(it.isPaidOut, true)
            where { (it.userId eq userId) and (it.cashbackRequestId eq payout.id) }
        }
        db.update(CashbackRequests) {
            set(it.status, "finished") //here is error
            where { it.id eq payout.id }
        }
        return payout
    }

    fun getActivePayout(userId: Int): PayoutInfo? {
        return db.cashbackRequests.firstOrNull {
            (it.userId eq userId) and (it.status eq "pending")
        }?.let { PayoutInfo(it.id, it.amount, it.dateCreated) }
    }

    fun getPayouts(start: Int = 0, count: Int = 10, dateStart: LocalDateTime? = null, dateEnd: LocalDateTime? = null,
                   userMail: String? = null): Pair<Int, List<CashbackPayout>> {
        val query = db.from(CashbackRequests)
            .let {
                it.innerJoin(AccessControl.Users, on = CashbackRequests.userId eq AccessControl.Users.id)
            }
            .select()
            .whereWithConditions {
                if(dateStart != null) {
                    it += CashbackRequests.dateCreated greaterEq dateStart
                }
                if(dateEnd != null) {
                    it += CashbackRequests.dateCreated lessEq dateEnd
                }
                if(userMail != null) {
                    val trimmedUserMail = userMail.trim().lowercase()
                    it += AccessControl.Users.mail like "%$trimmedUserMail%"
                }
            }

        val countFound = query.totalRecordsInAllPages
        if (countFound == 0) return 0 to emptyList()

        val result = query
            .limit(start, count)
            .map { CashbackPayout(
                id = it[CashbackRequests.id]!!,
                userId = it[CashbackRequests.userId]!!,
                userName = it[AccessControl.Users.name]!!,
                userMail = it[AccessControl.Users.mail]!!,
                amount = it[CashbackRequests.amount]!!,
                isActive = it[CashbackRequests.status] == "pending",
                dateCreated = it[CashbackRequests.dateCreated]!!
            ) }
        return countFound to result
    }

    fun getCashbackOrders(start: Int = 0, count: Int = 10, isPaid: Boolean? = null, userId: Int? = null,
                     dateStart: LocalDateTime? = null, dateEnd: LocalDateTime? = null, userMail: String? = null): Pair<Int, List<CashbackOrder>> {
//        val currencyMgr: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)

        val query = db.from(Cashback)
            .let { query ->
                query.innerJoin(OrdersManager.Orders, on = Cashback.orderId eq OrdersManager.Orders.id)
                    .innerJoin(AccessControl.Users, on = AccessControl.Users.id eq Cashback.userId)
            }
            .select()
            .whereWithConditions {
                if(isPaid != null) {
                    it += Cashback.isPaidOut eq isPaid
                }
                if(userId != null) {
                    it += Cashback.userId eq userId
                }
                if(userMail != null) {
                    val trimmedMail = userMail.trim().lowercase()
                    it += AccessControl.Users.mail like "%$trimmedMail%"
                }
                if(dateStart != null) {
                    it += Cashback.dateCreated greaterEq dateStart
                }
                if(dateEnd != null) {
                    it += Cashback.dateCreated lessEq dateEnd
                }
            }

        val countFound = query.totalRecordsInAllPages
        if (countFound == 0) return 0 to emptyList()

        val result = query
            .limit(start, count)
            .map {
//                val from = currencyMgr.getCurrencyById(it[OrdersManager.Orders.fromId]!!)
//                    ?: throw ValidationError("Валюта Отдаю не найдена")
//                val to = currencyMgr.getCurrencyById(it[OrdersManager.Orders.toId]!!)
//                    ?: throw ValidationError("Валюта Получаю не найдена")
                CashbackOrder(
                    id = it[Cashback.id]!!,
                    userId = it[Cashback.userId]!!,
                    orderId = it[Cashback.orderId]!!,
                    userMail = it[AccessControl.Users.mail]!!,
                    userName = it[AccessControl.Users.name]!!,
                    amountFrom = it[OrdersManager.Orders.amountFrom]!!,
                    amountTo = it[OrdersManager.Orders.amountTo]!!,
                    fromCode = it[OrdersManager.Orders.fromCode]!!,
                    fromName = it[OrdersManager.Orders.fromName]!!,
                    toCode = it[OrdersManager.Orders.toCode]!!,
                    toName = it[OrdersManager.Orders.toName]!!,
                    earnings = it[Cashback.earnings]!!,
                    isPaidOut = it[Cashback.isPaidOut]!!,
                    dateCreated = it[Cashback.dateCreated]!!,
                    profit = it[OrdersManager.Orders.profit]!!,
                    orderValue = it[OrdersManager.Orders.orderValue]!!
                )
            }

        return countFound to result
    }

    fun getSum(userId: Int? = null): Double? {
        if(userId != null) {
            return db.cashback
                .filter { it.userId eq userId }
                .aggregateColumns { sum(it.earnings) }
        }
        return db.cashback
            .aggregateColumns { sum(it.earnings) }
    }

    fun getSumPayed(userId: Int? = null): Double? {
        if(userId != null) {
            return db.cashback
                .filter { (it.userId eq userId) and (it.isPaidOut eq true) }
                .aggregateColumns { sum(it.earnings) }
        }
        return db.cashback
            .filter { it.isPaidOut eq true }
            .aggregateColumns { sum(it.earnings) }
    }
}

interface CashbackRecord: Entity<CashbackRecord> {
    companion object: Entity.Factory<CashbackRecord>()
    var id: Int
    var userId: Int
    var orderId: Int
    var earnings: Double
    var isPaidOut: Boolean
    var dateCreated: LocalDateTime
    val cashbackRequestId: Int
}

interface CashbackRequestRecord: Entity<CashbackRequestRecord> {
    companion object: Entity.Factory<CashbackRequestRecord>()
    var id: Int
    var userId: Int
    var dateCreated: LocalDateTime
    var amount: Double
    var status: String
}

@Serializable
data class CashbackPayout(
    val id: Int,
    val userId: Int,
    val userName: String,
    val userMail: String,
    val amount: Double,
    val isActive: Boolean,
    @Serializable(with=LocalDateTimeSerializer::class)
    val dateCreated: LocalDateTime
)

@Serializable
data class CashbackOrder(
    val id: Int,
    val userId: Int,
    val orderId: Int,
    val userMail: String,
    val userName: String,
    val amountFrom: Double,
    val amountTo: Double,

    //валюты:
    val fromCode: String,
    val toCode: String,
    val fromName: String,
    val toName: String,

    val earnings: Double,
    val isPaidOut: Boolean,
    @Serializable(with = LocalDateTimeSerializer::class)
    val dateCreated: LocalDateTime,
    val profit: Double,
    val orderValue: Double
)