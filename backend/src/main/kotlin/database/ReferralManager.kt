package com.github.perelshtein.database

import com.github.perelshtein.AccessControl
import com.github.perelshtein.BONUS_TYPE
import com.github.perelshtein.COOKIE_AUTH_TOKEN
import com.github.perelshtein.COOKIE_PRODUCTION
import com.github.perelshtein.COOKIE_REF_NAME
import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.Options
import com.github.perelshtein.OptionsManager
import com.github.perelshtein.ValidationError
import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall
import io.ktor.util.date.GMTDate
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.delete
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
import java.util.UUID
import kotlin.getValue

class ReferralManager: KoinComponent {
    object Referrals: Table<ReferralsRecord>("referral_earnings") {
        val id = int("id").primaryKey().bindTo { it.id }
        val refId = int("ref_id").bindTo { it.refId }
        val orderId = int("order_id").bindTo { it.orderId }
        val earnings = double("earnings").bindTo { it.earnings }
        val isPaidOut = boolean("is_paid_out").bindTo { it.isPaidOut }
        val dateCreated = datetime("date_created").bindTo { it.dateCreated }
        val payoutRequestId = int("payout_request_id").bindTo { it.payoutRequestId }
    }

    object PayoutRequests: Table<PayoutRequestRecord>("payout_requests") {
        val id = int("id").primaryKey().bindTo { it.id }
        val refId = int("ref_id").bindTo { it.refId }
        val dateCreated = datetime("date_created").bindTo { it.dateCreated }
        val amount = double("amount").bindTo { it.amount }
        val status = varchar("status").bindTo { it.status }
    }

    object ReferralSessions: Table<ReferralSessionsRecord>("referral_sessions") {
        val uuid = varchar("uuid").primaryKey().bindTo { it.uuid }
        val refId = int("ref_id").bindTo { it.refId }
        val dateCreated = datetime("date_created").bindTo { it.dateCreated }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val log = LoggerFactory.getLogger("ReferralManager")
    val Database.referrals get() = this.sequenceOf(Referrals)
    val Database.payoutRequests get() = this.sequenceOf(PayoutRequests)
    val Database.referralSessions get() = this.sequenceOf(ReferralSessions)
    private var cleanupCounter = 0

    fun generateRefCookie(refId: Int): String {
        val newUUID = UUID.randomUUID().toString()
        db.referralSessions.add(ReferralSessionsRecord {
            this.uuid = newUUID
            this.refId = refId
            this.dateCreated = LocalDateTime.now()
        })
        return newUUID
    }

    fun checkRefCookie(uuid: String, call: ApplicationCall): Int? {
        val optFromFile: Options by inject()
        db.referralSessions.firstOrNull { it.uuid eq uuid }?.let {
            db.delete(ReferralSessions) { it.uuid eq uuid }
            call.response.cookies.append(
                Cookie(
                    name = COOKIE_REF_NAME,
                    value = "game over",
                    httpOnly = true,
                    secure = COOKIE_PRODUCTION,
                    path="/",
                    maxAge = 0,
                    domain = if(COOKIE_PRODUCTION) optFromFile.host else null,
                    extensions = mapOf("SameSite" to "strict"),
                    expires = GMTDate()
                )
            )

            if(it.dateCreated.plusDays(7L).isAfter(LocalDateTime.now())) {
                return it.refId
            }
        }
        return null
    }

    fun calcBonus(order: OrdersRecord) {
        val accessControl: AccessControl by inject()
        val optionsManager: OptionsManager by inject()
        if(order.refId == null) return
        if(order.refId == order.userId) {
            log.warn("Реф.обмен для реферала id=${order.refId} не засчитан. id реферала совпадает с id пользователя")
            return
        }
        val refProfile = accessControl.getUserById(order.refId!!)
        if(refProfile == null) {
            log.error("Реферал с id=${order.userId} не найден")
            return
        }
        if(db.referrals.any { (it.orderId eq order.id) }) {
            log.warn("Бонус по заявке ${order.id} уже начислен. Реферал ${refProfile.name}")
            return
        }

        val percent = refProfile.referralPercent ?: optionsManager.getOptions().referralPercent
        if(BONUS_TYPE.fromValue(refProfile.referralType) == BONUS_TYPE.FROM_PROFIT) {
            val bonus = order.profit * percent / 100
            if(bonus == 0.0) {
                log.info("Реф.бонус по заявке ${order.id} не начислен. Реферал ${refProfile.name}")
                return
            }
            db.referrals.add(ReferralsRecord {
                this.refId = order.refId!!
                this.orderId = order.id
                this.earnings = bonus
                this.dateCreated = LocalDateTime.now()
            })
            log.info("Рефералу ${refProfile.name} начислен бонус ${bonus} USDT")
        }
        else {
            val bonus = order.orderValue * percent / 100
            if(bonus == 0.0) {
                log.info("Реф.бонус по заявке ${order.id} не начислен. Реферал ${refProfile.name}")
                return
            }
            db.referrals.add(ReferralsRecord {
                this.refId = order.refId!!
                this.orderId = order.id
                this.earnings = bonus
                this.dateCreated = LocalDateTime.now()
            })
            log.info("Рефералу ${refProfile.name} начислен бонус ${bonus} USDT")
        }
    }

    fun createPayout(refId: Int): PayoutInfo {
        val sum = db.referrals
            .filter { (it.refId eq refId) and (it.isPaidOut eq false) }
            .aggregateColumns { sum(it.earnings) }
        if(sum == null) throw ValidationError("Сумма к выплате = 0")

        val payout = PayoutRequestRecord {
            this.refId = refId
            this.dateCreated = LocalDateTime.now()
            this.amount = sum
        }
        db.payoutRequests.add(payout) //generating ID

        db.update(Referrals) {
            set(it.payoutRequestId, payout.id)
            where { (it.refId eq refId) and (it.isPaidOut eq false) }
        }
        payout.flushChanges()
        return PayoutInfo(payout.id, sum, payout.dateCreated)
    }

    fun finishPayout(refId: Int): PayoutInfo {
        val payout = getActivePayout(refId) ?: throw ValidationError("Активный запрос на выплату не найден")
        db.update(Referrals) {
            set(it.isPaidOut, true)
            where { (it.refId eq refId) and (it.payoutRequestId eq payout.id) }
        }
        db.update(PayoutRequests) {
            set(it.status, "finished")
            where { it.id eq payout.id }
        }
        return payout
    }

    fun getActivePayout(refId: Int): PayoutInfo? {
        return db.payoutRequests.firstOrNull {
            (it.refId eq refId) and (it.status eq "pending")
        }?.let { PayoutInfo(it.id, it.amount, it.dateCreated) }
    }

    fun getPayouts(start: Int = 0, count: Int = 10, dateStart: LocalDateTime? = null, dateEnd: LocalDateTime? = null,
           refMail: String? = null): Pair<Int, List<Payout>> {
        val query = db.from(PayoutRequests)
            .let {
                it.innerJoin(AccessControl.Users, on = PayoutRequests.refId eq AccessControl.Users.id)
            }
            .select()
            .whereWithConditions {
                if(dateStart != null) {
                    it += PayoutRequests.dateCreated greaterEq dateStart
                }
                if(dateEnd != null) {
                    it += PayoutRequests.dateCreated lessEq dateEnd
                }
                if(refMail != null) {
                    val trimmedRefMail = refMail.trim().lowercase()
                    it += AccessControl.Users.mail like "%$trimmedRefMail%"
                }
            }

        val countFound = query.totalRecordsInAllPages
        if (countFound == 0) return 0 to emptyList()

        val result = query
            .limit(start, count)
            .map { Payout(
                id = it[PayoutRequests.id]!!,
                refId = it[PayoutRequests.refId]!!,
                refName = it[AccessControl.Users.name]!!,
                refMail = it[AccessControl.Users.mail]!!,
                amount = it[PayoutRequests.amount]!!,
                isActive = it[PayoutRequests.status] == "pending",
                dateCreated = it[PayoutRequests.dateCreated]!!
            ) }
        return countFound to result
    }

    fun getReferrals(start: Int = 0, count: Int = 10, isPaid: Boolean? = null, refId: Int? = null,
             dateStart: LocalDateTime? = null, dateEnd: LocalDateTime? = null, refMail: String? = null): Pair<Int, List<ReferralOrder>> {
        val accessControl: AccessControl by inject()

        val query = db.from(Referrals)
            .innerJoin(OrdersManager.Orders, on = Referrals.orderId eq OrdersManager.Orders.id)
            .innerJoin(AccessControl.Users, on = AccessControl.Users.id eq OrdersManager.Orders.userId)
            .select()
            .whereWithConditions {
                if(isPaid != null) {
                    it += Referrals.isPaidOut eq isPaid
                }
                if(refId != null) {
                    it += Referrals.refId eq refId
                }
                if(refMail != null) {
                    val trimmedMail = refMail.trim().lowercase()
                    it += AccessControl.Users.mail like "%$trimmedMail%"
                }
                if(dateStart != null) {
                    it += Referrals.dateCreated greaterEq dateStart
                }
                if(dateEnd != null) {
                    it += Referrals.dateCreated lessEq dateEnd
                }
            }

        val countFound = query.totalRecordsInAllPages
        if (countFound == 0) return 0 to emptyList()

        val result = query
            .limit(start, count)
            .map {
                val ref = accessControl.getUserById(it[Referrals.refId]!!) ?: throw ValidationError("Реферал не найден")
                ReferralOrder(
                    id = it[Referrals.id]!!,
                    refId = it[Referrals.refId]!!,
                    orderId = it[Referrals.orderId]!!,
                    userMail = it[AccessControl.Users.mail]!!,
                    userName = it[AccessControl.Users.name]!!,
                    amountFrom = it[OrdersManager.Orders.amountFrom]!!,
                    amountTo = it[OrdersManager.Orders.amountTo]!!,
                    fromCode = it[OrdersManager.Orders.fromCode]!!,
                    fromName = it[OrdersManager.Orders.fromName]!!,
                    toCode = it[OrdersManager.Orders.toCode]!!,
                    toName = it[OrdersManager.Orders.toName]!!,
                    earnings = it[Referrals.earnings]!!,
                    isPaidOut = it[Referrals.isPaidOut]!!,
                    dateCreated = it[Referrals.dateCreated]!!,
                    refMail = ref.mail,
                    refName = ref.name,
                    orderValue = it[OrdersManager.Orders.orderValue]!!,
                    profit = it[OrdersManager.Orders.profit]!!
                )
            }

        return countFound to result
    }

    fun getSum(refId: Int? = null): Double? {
        if(refId != null) {
            return db.referrals
                .filter { it.refId eq refId }
                .aggregateColumns { sum(it.earnings) }
        }
        return db.referrals
            .aggregateColumns { sum(it.earnings) }
    }

    fun getSumPayed(refId: Int? = null): Double? {
        if(refId != null) {
            return db.referrals
                .filter { (it.refId eq refId) and (it.isPaidOut eq true) }
                .aggregateColumns { sum(it.earnings) }
        }
        return db.referrals
            .filter { it.isPaidOut eq true }
            .aggregateColumns { sum(it.earnings) }
    }

    fun deleteOldSessions() {
        cleanupCounter++
        if(cleanupCounter % 10 == 0) {
            db.delete(ReferralSessions) { it.dateCreated lessEq LocalDateTime.now().minusDays(7) }
        }
    }
}

interface ReferralsRecord: Entity<ReferralsRecord> {
    companion object: Entity.Factory<ReferralsRecord>()
    var id: Int
    var refId: Int
    var orderId: Int
    var earnings: Double
    var isPaidOut: Boolean
//    @Serializable(with = LocalDateTimeSerializer::class)
    var dateCreated: LocalDateTime
    var payoutRequestId: Int?
}

interface PayoutRequestRecord: Entity<PayoutRequestRecord> {
    companion object: Entity.Factory<PayoutRequestRecord>()
    var id: Int
    var refId: Int
    var dateCreated: LocalDateTime
    var amount: Double
    var status: String
}

interface ReferralSessionsRecord: Entity<ReferralSessionsRecord> {
    companion object: Entity.Factory<ReferralSessionsRecord>()
    var uuid: String
    var refId: Int
    var dateCreated: LocalDateTime
}

@Serializable
data class PayoutInfo(
    val id: Int,
    val amount: Double,
    @Serializable(with = LocalDateTimeSerializer::class)
    val dateCreated: LocalDateTime
)

@Serializable
data class ReferralOrder(
    val id: Int,
    val refId: Int,
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
    val refMail: String,
    val refName: String,
    val orderValue: Double,
    val profit: Double
)

@Serializable
data class Payout(
    val id: Int,
    val refId: Int,
    val refName: String,
    val refMail: String,
    val amount: Double,
    val isActive: Boolean,
    @Serializable(with=LocalDateTimeSerializer::class)
    val dateCreated: LocalDateTime
)