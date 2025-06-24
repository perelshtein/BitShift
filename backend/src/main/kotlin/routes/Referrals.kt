package com.github.perelshtein.routes

import com.github.perelshtein.AccessControl
import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.Pagination
import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.Payout
import com.github.perelshtein.database.PayoutInfo
import com.github.perelshtein.database.ReferralManager
import com.github.perelshtein.database.ReferralOrder
import com.github.perelshtein.database.ReferralsRecord
import com.github.perelshtein.getUserId
import com.github.perelshtein.jsonMap
import com.github.perelshtein.respondApi
import com.github.perelshtein.withUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.getOrElse

fun Route.referrals() {
    val log = LoggerFactory.getLogger("Route.referrals")
    val mgr: ReferralManager = KoinJavaComponent.get(ReferralManager::class.java)
    val accessControl: AccessControl = KoinJavaComponent.get(AccessControl::class.java)

    // загружаем список бонусов по рефералам
    get("/referrals_orders") {
        runCatching {
            val start = call.parameters["start"]?.toIntOrNull() ?: 0
            val count = call.parameters["count"]?.toIntOrNull() ?: 10
            val status = call.parameters["status"]?.toBooleanStrictOrNull()
            val refMail = call.parameters["refMail"]
            val dateStart = call.parameters["dateStart"]?.let {
                LocalDateTime.parse(it)
            }
            val dateEnd = call.parameters["dateEnd"]?.let {
                LocalDateTime.parse(it)
            }
            val found = mgr.getReferrals(start = start, count = count, isPaid = status, refMail = refMail,
                dateStart = dateStart, dateEnd = dateEnd)
            call.respondApi(
                ApiResponse.Success(
                    data = Pagination(
                        items = found.second,
                        total = found.first,
                        page = start / count + 1,
                        pageSize = count
                    )
                ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
           throw exception
        }
    }

    // отмечаем запрос как выполненный
    post("/referrals_paid") {
        runCatching {
            val params = call.receive<Map<String, JsonElement>>()
            val refId = params["refId"]?.jsonPrimitive?.content?.toIntOrNull() ?: throw ValidationError("Не указан id реферала")
            val refMail = accessControl.getUserById(refId)?.mail ?: throw ValidationError("Реферал с id=${refId} не найден")
            val result = mgr.finishPayout(refId)
            val msg = "Выплата бонусов для реферала id=${refId} (${refMail}) на сумму ${result.amount} USDT выполнена"
            log.info(msg)
            val answer = jsonMap(
                "amount" to result.amount,
                "date" to result.dateCreated,
                "reqId" to result.id
            )
            call.respondApi(ApiResponse.Success(data = answer, message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    // список запросов на вывод, статистика
    get("/referrals_status") {
        val start = call.parameters["start"]?.toIntOrNull() ?: 0
        val count = call.parameters["count"]?.toIntOrNull() ?: 10
        val dateStart = call.parameters["dateStart"]?.let {
            LocalDateTime.parse(it)
        }
        val dateEnd = call.parameters["dateEnd"]?.let {
            LocalDateTime.parse(it)
        }
        val refMail = call.parameters["refMail"]
        val mgr: ReferralManager = KoinJavaComponent.get(ReferralManager::class.java)
        runCatching {
            val answer = ReferralsForAdmin(
                sum = mgr.getSum() ?: 0.0,
                payed = mgr.getSumPayed() ?: 0.0,
                payouts = mgr.getPayouts(start, count, dateStart, dateEnd, refMail)
            )
            call.respondApi(ApiResponse.Success(
                data = answer
            ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    //пользов - список запросов на вывод, статус запроса, баланс
    get("/user/referrals_status") {
        runCatching {
            val refId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val answer = ReferralsForUser(
                sum = mgr.getSum(refId) ?: 0.0,
                payed = mgr.getSumPayed(refId) ?: 0.0,
                activePayout = mgr.getActivePayout(refId)
            )
            call.respondApi(ApiResponse.Success(
                data = answer
            ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    //пользов запрос на вывод
    post("/user/referrals_withdraw") {
        runCatching {
            val refId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            if(mgr.getActivePayout(refId) != null) {
                throw ValidationError("Запрос на выплату бонусов уже был отправлен. Дождитесь завершения.")
            }
            val result = mgr.createPayout(refId)
            val msg = "Создан запрос на выплату реферальных бонусов, сумма - ${result.amount} USDT"
            log.withUser(call, msg)
            val answer = jsonMap(
                "amount" to result.amount,
                "date" to result.dateCreated,
                "reqId" to result.id
            )
            call.respondApi(
                response = ApiResponse.Success(
                    data = answer,
                    message = msg
                ),
                statusCode = HttpStatusCode.Created
            )
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    // пользов - мои реф.обмены
    get("/user/referrals_orders") {
        runCatching {
            val refId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val start = call.parameters["start"]?.toIntOrNull() ?: 0
            val count = call.parameters["count"]?.toIntOrNull() ?: 10
            val dateStart = call.parameters["dateStart"]?.let {
                LocalDateTime.parse(it)
            }
            val dateEnd = call.parameters["dateEnd"]?.let {
                LocalDateTime.parse(it)
            }
            val found = mgr.getReferrals(start = start, count = count, refId = refId,
                dateStart = dateStart, dateEnd = dateEnd)
            call.respondApi(
                ApiResponse.Success(
                    data = Pagination(
                        items = found.second,
                        total = found.first,
                        page = start / count + 1,
                        pageSize = count
                    )
                ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}

@Serializable
data class ReferralsForUser(
    val sum: Double,
    val payed: Double,
    val activePayout: PayoutInfo? = null
)

@Serializable
data class ReferralsForAdmin(
    val sum: Double,
    val payed: Double,
    val payouts: Pair<Int,List<Payout>>
)