package com.github.perelshtein.routes

import com.github.perelshtein.AccessControl
import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.Pagination
import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.CashbackManager
import com.github.perelshtein.database.CashbackOrder
import com.github.perelshtein.database.CashbackPayout
import com.github.perelshtein.database.PayoutInfo
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

fun Route.cashback() {
    val log = LoggerFactory.getLogger("Route.cashback")
    val mgr: CashbackManager = KoinJavaComponent.get(CashbackManager::class.java)
    val accessControl: AccessControl = KoinJavaComponent.get(AccessControl::class.java)

    // загружаем список заявок с бонусами
    get("/cashback_orders") {
        runCatching {
            val start = call.parameters["start"]?.toIntOrNull() ?: 0
            val count = call.parameters["count"]?.toIntOrNull() ?: 10
            val status = call.parameters["status"]?.toBooleanStrictOrNull()
            val userMail = call.parameters["userMail"]
            val dateStart = call.parameters["dateStart"]?.let {
                LocalDateTime.parse(it)
            }
            val dateEnd = call.parameters["dateEnd"]?.let {
                LocalDateTime.parse(it)
            }
            val found = mgr.getCashbackOrders(start = start, count = count, isPaid = status, userMail = userMail,
                dateStart = dateStart, dateEnd = dateEnd)
            call.respondApi(
                ApiResponse.Success(
                    data = Pagination(
                        items = found.second,
                        total =  found.first,
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
    post("/cashback_paid") {
        runCatching {
            val params = call.receive<Map<String, JsonElement>>()
            val userId = params["userId"]?.jsonPrimitive?.content?.toIntOrNull() ?: throw ValidationError("Не указан id пользователя")
            val userMail = accessControl.getUserById(userId)?.mail ?: throw ValidationError("Пользователь с id=${userId} не найден")
            val result = mgr.finishPayout(userId)
            val msg = "Выплата кэшбека для пользователя id=${userId} (${userMail}) на сумму ${result.amount} USDT выполнена"
            log.info(msg)
            call.respondApi(
                response = ApiResponse.Success(
                    data = jsonMap(
                        "amount" to result.amount,
                        "date" to result.dateCreated,
                        "reqId" to result.id
                    ),
                    message = msg
                )
            )
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    // список запросов на вывод, статистика
    get("/cashback_status") {
        val start = call.parameters["start"]?.toIntOrNull() ?: 0
        val count = call.parameters["count"]?.toIntOrNull() ?: 10
        val dateStart = call.parameters["dateStart"]?.let {
            LocalDateTime.parse(it)
        }
        val dateEnd = call.parameters["dateEnd"]?.let {
            LocalDateTime.parse(it)
        }
        val userMail = call.parameters["userMail"]
        val mgr: CashbackManager = KoinJavaComponent.get(CashbackManager::class.java)
        runCatching {
            val answer = CashbackForAdmin(
                sum = mgr.getSum() ?: 0.0,
                payed = mgr.getSumPayed() ?: 0.0,
                payouts = mgr.getPayouts(start, count, dateStart, dateEnd, userMail)
            )
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    //пользов - список запросов на вывод, статус запроса, баланс
    get("/user/cashback_status") {
        runCatching {
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val answer = CashbackForUser(
                sum = mgr.getSum(userId) ?: 0.0,
                payed = mgr.getSumPayed(userId) ?: 0.0,
                activePayout = mgr.getActivePayout(userId)
            )
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    //пользов запрос на вывод
    post("/user/cashback_withdraw") {
        runCatching {
            val refId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            if(mgr.getActivePayout(refId) != null) {
                throw ValidationError("Запрос на выплату кэшбека уже был отправлен. Дождитесь завершения.")
            }
            val result = mgr.createPayout(refId)
            val msg = "Создан запрос на выплату кэшбека, сумма - ${result.amount} USDT"
            log.withUser(call, msg)
            call.respondApi(
                response = ApiResponse.Success(
                    data = jsonMap(
                        "amount" to result.amount,
                        "date" to result.dateCreated,
                        "reqId" to result.id
                    ),
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
    get("/user/cashback_orders") {
        runCatching {
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val start = call.parameters["start"]?.toIntOrNull() ?: 0
            val count = call.parameters["count"]?.toIntOrNull() ?: 10
            val dateStart = call.parameters["dateStart"]?.let {
                LocalDateTime.parse(it)
            }
            val dateEnd = call.parameters["dateEnd"]?.let {
                LocalDateTime.parse(it)
            }
            val found = mgr.getCashbackOrders(start = start, count = count, userId = userId,
                dateStart = dateStart, dateEnd = dateEnd)
            call.respondApi(
                ApiResponse.Success(
                    data = Pagination(
                        items = found.second,
                        total = found.first,
                        page = start / count + 1,
                        pageSize = count
                    )
                )
            )
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}

@Serializable
data class CashbackForUser(
    val sum: Double,
    val payed: Double,
    val activePayout: PayoutInfo? = null
)

@Serializable
data class CashbackForAdmin(
    val sum: Double,
    val payed: Double,
    val payouts: Pair<Int,List<CashbackPayout>>
)