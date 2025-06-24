package com.github.perelshtein.routes

import com.github.perelshtein.ACTION
import com.github.perelshtein.AccessControl
import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.Mail
import com.github.perelshtein.NotifySender
import com.github.perelshtein.Pagination
import com.github.perelshtein.TempOrder
import com.github.perelshtein.TempOrderStorage
import com.github.perelshtein.ValidationError
import com.github.perelshtein.checkEmail
import com.github.perelshtein.database.CurrencyManager
import com.github.perelshtein.database.DirectionsManager
import com.github.perelshtein.database.ORDER_SRC
import com.github.perelshtein.database.OrderAdminDTO
import com.github.perelshtein.database.OrderHistoryDTO
import com.github.perelshtein.database.OrderUserDTO
import com.github.perelshtein.database.OrdersManager
import com.github.perelshtein.database.OrdersRecord
import com.github.perelshtein.database.ReferralManager
import com.github.perelshtein.database.getTimeInterval
import com.github.perelshtein.database.toAdminDTO
import com.github.perelshtein.database.toDTO
import com.github.perelshtein.database.toUserDTO
import com.github.perelshtein.exchanges.Autopay
import com.github.perelshtein.exchanges.LimitChecker
import com.github.perelshtein.getUserId
import com.github.perelshtein.jsonMap
import com.github.perelshtein.respondApi
import com.github.perelshtein.roundup
import com.github.perelshtein.trimmed
import com.github.perelshtein.withUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.Random
import kotlin.jvm.java

fun Route.orders() {
    val log = LoggerFactory.getLogger("Route.orders")
    val ordersManager: OrdersManager = KoinJavaComponent.get(OrdersManager::class.java)
    val currencyManager: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)

    fun idToXml(id: Int?, msg: String): String? {
        if(id == null) return null
        return currencyManager.getCurrencyById(id)?.xmlCode ?: throw ValidationError("Валюта ${msg} не найдена")
    }

    get("/orders") {
        runCatching {
            log.withUser(call, "Запрос списка заявок", LOG_TYPE.DEBUG)
            val start = call.parameters["start"]?.toInt() ?: 0
            val count = call.parameters["count"]?.toInt() ?: 10
            val status = call.parameters["status"]
            val filter = call.parameters["filter"]
            val userId = call.parameters["userId"]?.toInt()
            val fromId = call.parameters["fromId"]?.toInt()
            val toId = call.parameters["toId"]?.toInt()
            val id = call.parameters["id"]?.toInt()

            //если указан id, загрузим только один ордер
            if(id != null) {
                val answer = ordersManager.getOrder(id) ?: throw ValidationError("Заявка с id=${id} не найдена")
                call.respondApi(ApiResponse.Success(
                        data = Pagination(
                            items = listOf(answer.toAdminDTO()),
                            total = 1,
                            page = 1,
                            pageSize = 1
                    ))
                )
                return@get
            }

            val dateStart = call.parameters["dateStart"]?.let {
                LocalDateTime.parse(it)
            }
            val dateEnd = call.parameters["dateEnd"]?.let {
                LocalDateTime.parse(it)
            }
            val found = ordersManager.getOrders(
                start, count, status, filter, userId, idToXml(fromId, "Отдаю"), idToXml(toId, "Получаю"),
                dateStart, dateEnd
            )
            call.respondApi(
                ApiResponse.Success(
                    data = Pagination(
                        items = found.second.map{ convertToDetailedOrder(it.toAdminDTO()) },
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

    get("/order/{id}") {
        runCatching {
            val id = call.parameters["id"]?.toInt() ?: throw ValidationError("Не указан id заявки")
            log.withUser(call, "Загрузка заявки с id=${id}", LOG_TYPE.DEBUG)
            val answer = ordersManager.getOrder(id) ?: throw ValidationError("Заявка с id=${id} не найдена")
            call.respondApi(ApiResponse.Success(
                data = convertToDetailedOrder(answer.toAdminDTO())
            ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    // редакт заявок из админки
    // создание - только с сайта
    post("/order") {
        runCatching {
            val log = LoggerFactory.getLogger("Route.orders")
            val body = call.receive<Map<String, JsonElement>>().trimmed()
            val ordersManager: OrdersManager = KoinJavaComponent.get(OrdersManager::class.java)

            val ids = body["ids"]?.jsonArray?.map { it.jsonPrimitive.content.toInt() }
            if(ids == null) throw ValidationError("Не указаны id заявок (ids)")

            ids.forEach { id ->
                ordersManager.upsertOrder(ORDER_SRC.ADMIN_PANEL, OrdersRecord {
                    val oldOrder = ordersManager.getOrder(id) ?: throw ValidationError("Заявка с id=${id} не найдена")
                    this.id = id
                    body["status"]?.jsonPrimitive?.content?.let { this.status = it }
                    body["requisites"]?.jsonPrimitive?.content?.let { this.requisites = it }
                    body["profit"]?.jsonPrimitive?.content?.let { this.profit = it.toDouble() }
                    body["walletFrom"]?.jsonPrimitive?.content?.let { this.walletFrom = it }
                    // менять курс из админки только если направл с ручным приемом или отправкой
                    if(oldOrder.isManualReceive) {
                        body["rateGive"]?.jsonPrimitive?.content?.let { this.rateFrom = it.toDouble() }
                        if(this.status == "payed" && this.rateFrom == 0.0) {
                            throw ValidationError("Ручной прием средств: укажите курс")
                        }
                    }
                    if(oldOrder.isManualSend) {
                        body["rateGet"]?.jsonPrimitive?.content?.let { this.rateTo = it.toDouble() }
                        if(this.status == "completed" && this.rateTo == 0.0) {
                            throw ValidationError("Ручная отправка средств: укажите курс")
                        }
                    }
                    if(status == "deleted") {
                        val autopay: Autopay = KoinJavaComponent.get(Autopay::class.java)
                        autopay.deleteOrder(userId)
                    }
                })
            }

            val msg = if(ids.size == 1) "Заявка отредактирована успешно" else "Заявки отредактированы успешно"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    // Следующие маршруты - для запросов с сайта
    get("/user/orders") {
        runCatching {
            log.withUser(call, "Запрос списка заявок", LOG_TYPE.DEBUG)
            val _start = call.parameters["start"]?.toInt() ?: 0
            val _count = call.parameters["count"]?.toInt() ?: 10
            val _status = call.parameters["status"]
            val _fromId = call.parameters["fromId"]?.toInt()
            val _toId = call.parameters["toId"]?.toInt()
            val _dateStart = call.parameters["dateStart"]?.let {
                LocalDateTime.parse(it)
            }
            val _dateEnd = call.parameters["dateEnd"]?.let {
                LocalDateTime.parse(it)
            }

            val found = ordersManager.getOrders(
                start = _start, count = _count, status = _status, userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте"),
                fromXmlCode = idToXml(_fromId, "Отдаю"), toXmlCode = idToXml(_toId, "Получаю"), dateStart = _dateStart, dateEnd = _dateEnd
            )
            call.respondApi(
                ApiResponse.Success(
                    data = Pagination(
                        items = found.second.map { it.toUserDTO() },
                        total = found.first,
                        page = _start / _count + 1,
                        pageSize = _count
                    )
                ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/user/order/{id}") {
        runCatching {
            val id = call.parameters["id"]?.toInt() ?: throw ValidationError("Не указан id заявки")
            log.withUser(call, "Загрузка заявки с id=${id}", LOG_TYPE.DEBUG)
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val answer = ordersManager.getOrder(id)?.takeIf { it.userId == userId } ?: throw ValidationError("Заявка с id=${id} не найдена")
            call.respondApi(ApiResponse.Success(
                data = answer.toUserDTO().apply { deleteInterval = answer.getTimeInterval() }
            ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    delete("/user/order/{id}") {
        runCatching {
            val autopay: Autopay = KoinJavaComponent.get(Autopay::class.java)
            val id = call.parameters["id"]?.toInt() ?: throw ValidationError("Не указан id заявки")
            log.withUser(call, "Удаление заявки с id=${id}", LOG_TYPE.DEBUG)
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val order = ordersManager.getOrder(id)?.takeIf { it.userId == userId } ?: throw ValidationError("Заявка с id=${id} не найдена")
            ordersManager.upsertOrder(ORDER_SRC.USER, order.apply {
                status = "deleted"
            })
            autopay.deleteOrder(userId)
            val msg = "Заявка удалена"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    // переведем временную заявку в постоянную, свяжем ее с id пользователя
    put("/user/order/claim") {        
        val storage: TempOrderStorage = KoinJavaComponent.get(TempOrderStorage::class.java)

        runCatching {
            val body = call.receive<Map<String, JsonElement>>().trimmed()
            val tempId = body["tempOrderId"]?.jsonPrimitive?.content ?: throw ValidationError("Не указан id временной заявки")
            log.info("Ищем временную заявку $tempId")
            val tempOrder = storage.get(tempId) ?: throw ValidationError("Временная заявка не найдена")
            storage.delete(tempId)

            validateDirection(tempOrder)

            // если у пользов уже есть активная заявка, новую не создаем
            // покажем старую + сообщение об ошибке
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            ordersManager.getActiveOrder(userId)?.let {
                call.respondApi(
                    response = ApiResponse.Warning(
                        data = jsonMap("id" to it.id),
                        message = "У вас уже есть активная заявка",
                        action = ACTION.CHECKUP_ORDER
                    ),
                    statusCode = HttpStatusCode.Conflict
                )
                return@put
            }

            bindReferral(call)
            val orderId = createOrder(userId, tempOrder)
            call.respondApi(
                response = ApiResponse.Success(
                    data = jsonMap("id" to orderId),
                    message = "Заявка успешно подтверждена",
                    action = ACTION.CHECKUP_ORDER
                ),
                statusCode = HttpStatusCode.Created
            )
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    put("/user/order/txid") {
        runCatching {
            val autoPay: Autopay = KoinJavaComponent.get(Autopay::class.java)
            val body = call.receive<Map<String, JsonElement>>().trimmed()
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val txId = body["txId"]?.jsonPrimitive?.content ?: throw ValidationError("Не задан txId")
            if (!txId.matches(Regex("^0x[a-fA-F0-9]{64}$"))) throw ValidationError("Неправильный формат txId")
            autoPay.saveTxId(userId, txId)
            call.respondApi<Unit>(ApiResponse.Success(message = "TxID успешно сохранен"))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}

fun Route.putOrder() {
    val log = LoggerFactory.getLogger("Route.orders")

    put("/api/user/order") {
        val ordersManager: OrdersManager = KoinJavaComponent.get(OrdersManager::class.java)
        val accessControl: AccessControl = KoinJavaComponent.get(AccessControl::class.java)
        val mail: Mail = KoinJavaComponent.get(Mail::class.java)
        val storage: TempOrderStorage = KoinJavaComponent.get(TempOrderStorage::class.java)

        runCatching {
            val body = call.receive<Map<String, JsonElement>>().trimmed()
            val email = body["email"]?.jsonPrimitive?.content ?: throw ValidationError("Не указан email")
            if(!email.checkEmail()) throw ValidationError("Адрес почты указан в неправильном формате")
            val currencyFromIdParam = body["currencyFromId"]?.jsonPrimitive?.content?.toIntOrNull() ?:
                throw ValidationError("Не указана валюта Отдаю")
            val currencyToIdParam = body["currencyToId"]?.jsonPrimitive?.content?.toIntOrNull() ?:
                throw ValidationError("Не указана валюта Получаю")
            val walletParam = body["wallet"]?.jsonPrimitive?.content
            if(walletParam == null || walletParam.isBlank()) throw ValidationError("Не указан адрес кошелька")
            val amountFromParam = body["amountFrom"]?.jsonPrimitive?.content?.toDoubleOrNull() ?:
                throw ValidationError("Не указано количество валюты Отдаю")
            val amountToParam = body["amountTo"]?.jsonPrimitive?.content?.toDoubleOrNull() ?:
                throw ValidationError("Не указано количество валюты Получаю")
            val refId = body["rid"]?.jsonPrimitive?.content?.toIntOrNull()
            if(amountFromParam < 0.0 || amountToParam < 0.0) throw ValidationError("Количество валюты для обмена должно быть больше нуля")
            val giveFields =
                body["giveFields"]?.jsonObject?.entries?.associate { it.key to it.value.jsonPrimitive.content }
            val getFields =
                body["getFields"]?.jsonObject?.entries?.associate { it.key to it.value.jsonPrimitive.content }

            val request = TempOrder(currencyFromId = currencyFromIdParam, currencyToId = currencyToIdParam,
                wallet = walletParam, amountFrom =amountFromParam, amountTo = amountToParam, fieldsGive = giveFields,
                fieldsGet = getFields, refId = refId)
            validateDirection(request)

            //Есть ли пользователь с таким логином?
            val user = accessControl.getUserByName(email) ?: accessControl.getUserByMail(email)
            //Нет. Отправим ссылку для подтв регистрации
            if (user == null) {
                mail.sendVerificationEmail(email)
                log.info("Отправляем ссылку для подтверждения $email")
                val tempOrderId = storage.add(request)
                call.respondApi(
                    response = ApiResponse.Success(
                        message = "Вам отправлено письмо на $email для подтверждения регистрации. Пожалуйста, перейдите по ссылке.",
                        data = jsonMap("tempOrderId" to tempOrderId)
                    ),
                    statusCode = HttpStatusCode.Accepted
                )
                return@put
            }

            //Есть. Авторизован ли пользователь на сайте?
            val userId = call.getUserId()
            if (userId != user.id) {
                //Нет. Создадим временную заявку и отправим сообщ, что нужно авторизоваться
                log.info("Создаем временную заявку. Пользователь $email не авторизован.")
                val tempOrderId = storage.add(request)
                val answer = jsonMap(
                    "login" to email,
                    "tempOrderId" to tempOrderId
                )
                call.respondApi(
                    response = ApiResponse.Warning(
                        message = "Для создания заявки нужно войти с логином и паролем.",
                        data = answer,
                        action = ACTION.LOGIN
                    ),
                    statusCode = HttpStatusCode.Accepted
                )
                return@put
            }

            //Да, авторизован. Есть ли у пользователя активная заявка?
            ordersManager.getActiveOrder(userId)?.let {
                //Есть.
                call.respondApi(
                    response = ApiResponse.Warning(
                        message = "У вас уже есть активная заявка",
                        data = jsonMap("id" to it.id),
                        action = ACTION.ORDER_BY_ID
                    ),
                    statusCode = HttpStatusCode.Conflict
                )
                return@put
            }

            //Нет. Создадим новую заявку
            bindReferral(call)
            val orderId = createOrder(userId, request)
            val msg = "Заявка создана"
            log.withUser(call, msg)
            call.respondApi(
                response = ApiResponse.Success(
                    message = msg,
                    data = jsonMap("id" to orderId),
                    action = ACTION.ORDER_BY_ID
                ),
                statusCode = HttpStatusCode.Created
            )
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}

fun xmlToId(xml: String, msg: String): Int? {
    val currencyManager: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)
    return currencyManager.getCurrencyByXmlCode(xml)?.id //?: throw ValidationError("Валюта ${msg} не найдена")
}

fun convertToDetailedOrder(it: OrderAdminDTO): OrderAdminDetailedDTO {
    val currencyMgr: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)
    val accessControl: AccessControl = KoinJavaComponent.get(AccessControl::class.java)

    val fidelify = currencyMgr.getCurrencyByXmlCode(it.fromXmlCode)?.fidelity ?: 0
    val crs = if(fidelify > 0 && it.amountTo > 0.0) (it.amountFrom / it.amountTo).roundup(fidelify) else 0.0
    val fromId = xmlToId(it.fromXmlCode, "Отдаю")
    val toId = xmlToId(it.toXmlCode, "Получаю")
    return OrderAdminDetailedDTO(
        id = it.id,
        userId = it.userId,
        userName = accessControl.getUserById(it.userId)?.name ?: "Неизвестный пользователь",
        userMail = accessControl.getUserById(it.userId)?.mail ?: "Почта не указана",
        dateCreated = it.dateCreated,
        dateUpdated = it.dateUpdated,
        walletFrom = it.walletFrom,
        walletTo = it.walletTo,
        requisites = it.requisites,
        profit = it.profit,
        status = it.status,
        isActive = it.isActive,
        course = crs,
        from = OrderAdminDetailedDTO.Currency(
            id = fromId ?: 0,
            name = currencyMgr.getCurrencyById(fromId ?: 0)?.name ?: "Неизвестная валюта",
            code = currencyMgr.getCurrencyById(fromId ?: 0)?.code ?: "???",
            amount = it.amountFrom
        ),
        to = OrderAdminDetailedDTO.Currency(
            id = toId ?: 0,
            name = currencyMgr.getCurrencyById(toId ?: 0)?.name ?: "Неизвестная валюта",
            code = currencyMgr.getCurrencyById(toId ?: 0)?.code ?: "???",
            amount = it.amountTo
        ),
        fieldsGive = it.fieldsGive,
        fieldsGet = it.fieldsGet,
        isManualGive = it.isManualGive,
        isManualGet = it.isManualGet,
        rateGive = it.rateGive,
        rateGet = it.rateGet,
        statusHistory = it.statusHistory
    )
}

suspend fun validateDirection(t: TempOrder) {
    val directionsManager: DirectionsManager = KoinJavaComponent.get(DirectionsManager::class.java)
    val currencyManager: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)
    val checker: LimitChecker = KoinJavaComponent.get(LimitChecker::class.java)

    val found = directionsManager.getDirections(count = 1, fromId = t.currencyFromId, toId = t.currencyToId)
    if (found.first == 0) {
        throw ValidationError("Направление обмена не найдено")
    }
    val dir = directionsManager.getDirection(found.second.first().id)!!.toDTO()
    val from = currencyManager.getCurrencyById(t.currencyFromId) ?: throw ValidationError("Валюта Отдаю не найдена")
    val to = currencyManager.getCurrencyById(t.currencyToId) ?: throw ValidationError("Валюта Получаю не найдена")

    checker.validateLimits(
        amountParam = t.amountFrom,
        direction = dir,
        currencyCode = from.code,
        chainCode = from.payinChain,
        exchange = from.payin
    )

    checker.validateLimits(
        amountParam = t.amountTo,
        direction = dir,
        currencyCode = to.code,
        chainCode = to.payoutChain,
        exchange = to.payout
    )
}

fun bindReferral(call: ApplicationCall) {
    val log = LoggerFactory.getLogger("Route.orders")
    val referralManager: ReferralManager = KoinJavaComponent.get(ReferralManager::class.java)
    val accessControl: AccessControl = KoinJavaComponent.get(AccessControl::class.java)
    call.request.cookies["referral_session"]?.let { uuid ->
        referralManager.checkRefCookie(uuid, call)?.let { refId ->
            val ref = accessControl.getUserById(refId) ?: throw ValidationError("Реферал с id=${refId} не найден")
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val user = accessControl.getUserById(userId) ?: throw ValidationError("Пользователь с id=${call.getUserId()} не найден")
            if(user.referralId == null) {
                accessControl.updateUser(user.apply { this.referralId = refId })
                log.info("Пользователь ${user.mail} привязался к рефералу ${ref.mail}")
            }
            else log.warn("Пользователь ${user.mail} уже привязан к рефералу.")
        }
    }
}

suspend fun createOrder(
    userId: Int,
    request: TempOrder
): Int {
    val ordersManager: OrdersManager = KoinJavaComponent.get(OrdersManager::class.java)
    val currencyManager: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)
    val autopay: Autopay = KoinJavaComponent.get(Autopay::class.java)
    val notifySender: NotifySender = KoinJavaComponent.get(NotifySender::class.java)
    val accessControl: AccessControl = KoinJavaComponent.get(AccessControl::class.java)

    val refId = accessControl.getUserById(userId)?.referralId
    val from = currencyManager.getCurrencyById(request.currencyFromId) ?: throw ValidationError("Валюта Отдаю не найдена")
    val to = currencyManager.getCurrencyById(request.currencyToId) ?: throw ValidationError("Валюта Получаю не найдена")

    val newOrder = OrdersRecord {
        this.refId = refId
        this.userId = userId
        this.fromCode = from.code
        this.fromXmlCode = from.xmlCode
        this.fromName = from.name
        this.toCode = to.code
        this.toXmlCode = to.xmlCode
        this.toName = to.name
        this.walletTo = request.wallet
        this.amountFrom = request.amountFrom
        this.amountTo = request.amountTo
        this.status = "new"
        this.fieldsGive = Json.encodeToString(request.fieldsGive)
        this.fieldsGet = Json.encodeToString(request.fieldsGet)
        this.isManualReceive = from.payin == "manual"
        this.isManualSend = to.payout == "manual"
    }
    ordersManager.upsertOrder(ORDER_SRC.USER, newOrder)
    val activeOrder = ordersManager.getActiveOrder(userId) ?: throw ValidationError("Заявка не создана")
    autopay.addOrder(userId)
    notifySender.send(newOrder)
    return activeOrder.id
}

@Serializable
data class OrderAdminDetailedDTO(
    val id: Int,

    //чтобы убрать лишние запросы, загрузим сразу инф о пользователе
    val userId: Int, //если мало полей - грузите подробную инф о пользов
    val userName: String,
    val userMail: String,

    //и о валютах
    val from: Currency,
    val to: Currency,

    @Serializable(LocalDateTimeSerializer::class)
    val dateCreated: LocalDateTime,
    @Serializable(LocalDateTimeSerializer::class)
    val dateUpdated: LocalDateTime,
    val walletFrom: String,
    val walletTo: String,
    val requisites: String,
    val profit: Double,
    val status: String,
    val isActive: Boolean,
    val course: Double,
    val fieldsGive: Map<String, String>? = null,
    val fieldsGet : Map<String, String>? = null,
    val isManualGive: Boolean = false,
    val isManualGet: Boolean = false,
    val rateGive: Double,
    val rateGet: Double,
    val statusHistory: List<OrderHistoryDTO>,
    val refId: Int? = null
) {
    @Serializable
    data class Currency(
        val id: Int,
        val name: String,
        val code: String,
        val amount: Double
    )
}