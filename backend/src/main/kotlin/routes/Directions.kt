package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.Pagination
import com.github.perelshtein.ValidationError
import com.github.perelshtein.checkIds
import com.github.perelshtein.database.CurrencyFieldManager
import com.github.perelshtein.database.CurrencyFieldsBindManager
import com.github.perelshtein.database.CurrencyManager
import com.github.perelshtein.database.Direction
import com.github.perelshtein.database.DirectionDTO
import com.github.perelshtein.database.DirectionShortDTO
import com.github.perelshtein.database.DirectionUserDTO
import com.github.perelshtein.database.DirectionsManager
import com.github.perelshtein.database.FormulaManager
import com.github.perelshtein.database.toDB
import com.github.perelshtein.database.toDTO
import com.github.perelshtein.exchanges.Autopay
import com.github.perelshtein.exchanges.Bybit
import com.github.perelshtein.exchanges.LimitChecker
import com.github.perelshtein.exchanges.Mexc
import com.github.perelshtein.exchanges.PayInAPI
import com.github.perelshtein.exchanges.PayOutAPI
import com.github.perelshtein.exchanges.TradeLimits
import com.github.perelshtein.exchanges.Valute
import com.github.perelshtein.jsonMap
import com.github.perelshtein.respondApi
import com.github.perelshtein.roundup
import com.github.perelshtein.withUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.Random
import kotlin.math.max
import kotlin.math.min

fun Route.directions() {
    val log = LoggerFactory.getLogger("Route.directions")
    val mgr: DirectionsManager = KoinJavaComponent.get(DirectionsManager::class.java)
    val currencyMgr: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)
    val formulaMgr: FormulaManager = KoinJavaComponent.get(FormulaManager::class.java)

    fun validateStatus(status: String?): Boolean? {
        if(status != null && status !in listOf("active", "inactive")) {
            throw ValidationError("Недопустимый статус направления: $status")
        }
        if(status == "active") return true
        else if(status == "inactive") return false
        return null
    }


    fun checkDirCanEnabled(dir: Direction) {
        val give = currencyMgr.getCurrencyById(dir.fromId)
        val get = currencyMgr.getCurrencyById(dir.toId)
        if(give != null && get != null) {
            if(!give.isEnabled) {
                throw ValidationError("Нельзя активировать направление ${give.name} -> ${get.name}, пока валюта ${give.name} отключена")
            }
            if(!get.isEnabled) {
                throw ValidationError("Нельзя активировать направление ${give.name} -> ${get.name}, пока валюта ${get.name} отключена")
            }
        }
    }

    //Краткая инф о направлениях
    get("/directions") {
        runCatching {
            val start = call.parameters["start"]?.toInt() ?: 0
            val count = call.parameters["count"]?.toInt() ?: 10
            val isActive = validateStatus(call.parameters["status"])
            val filter = call.parameters["filter"]
            val fromId = call.parameters["fromId"]?.toInt()
            val toId = call.parameters["toId"]?.toInt()
            val found = mgr.getDirections(start, count, isActive, filter, fromId, toId)
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

    get("/findDirectionId") {
        runCatching {
            val from = call.parameters["from"] ?: throw ValidationError("Не указана валюта Отдаю (from)")
            val to = call.parameters["to"] ?: throw ValidationError("Не указана валюта Получаю (to)")
            val msg = mgr.getDirByXmlCode(from, to)
            call.respondApi(ApiResponse.Success(
                data = jsonMap("dirId" to msg))
            )
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    //массовое редактирование направлений
    post("/directions") {
        runCatching {
            val params = call.receive<Map<String, JsonElement>>()
            if(params["ids"] == null) throw ValidationError("Не указаны id направлений")
            var ids = Json.decodeFromString<List<Int>>(params["ids"].toString())
            if(ids.isEmpty()) throw ValidationError("Не указаны id направлений")

            var profit: Double? = null
            var isActive: Boolean? = null
            var minSum: Double? = null
            var maxSum: Double? = null
            var minSumCurrency: Int? = null
            var maxSumCurrency: Int? = null

            params["profit"]?.jsonPrimitive?.floatOrNull?.let {
                if(it < 0) throw ValidationError("Прибыль не может быть отрицательной")
                profit = it.toDouble().roundup(3)
            }

            params["isActive"]?.jsonPrimitive?.booleanOrNull?.let {
                isActive = it
            }

            params["minSumCurrency"]?.jsonPrimitive?.intOrNull?.let {
                if(currencyMgr.getCurrencyById(it) == null) throw ValidationError("Валюта, в которой задана мин.сумма," +
                    " не существует")
                minSumCurrency = it
            }

            params["maxSumCurrency"]?.jsonPrimitive?.intOrNull?.let {
                if(currencyMgr.getCurrencyById(it) == null) throw ValidationError("Валюта, в которой задана макс.сумма," +
                    " не существует")
                maxSumCurrency = it
            }

            params["minSum"]?.jsonPrimitive?.floatOrNull?.let {
                if(minSumCurrency == null) throw ValidationError("Не указана валюта, в которой задана мин.сумма")
                if(it < 0) throw ValidationError("Минимальная сумма не может быть отрицательной")
                val digits = currencyMgr.getCurrencyById(minSumCurrency)?.fidelity
                if(digits == null) throw ValidationError("Не удается определить точность валюты для мин.суммы")
                minSum = it.toDouble().roundup(digits)
            }

            params["maxSum"]?.jsonPrimitive?.floatOrNull?.let {
                if(maxSumCurrency == null) throw ValidationError("Не указана валюта, в которой задана макс.сумма")
                if(it < 0) throw ValidationError("Максимальная сумма не может быть отрицательной")
                val digits = currencyMgr.getCurrencyById(maxSumCurrency)?.fidelity
                if(digits == null) throw ValidationError("Не удается определить точность валюты для макс.суммы")
                maxSum = it.toDouble().roundup(digits)
            }

            if(isActive == true) {
                //если любая из валют отключена, направление активировать нельзя
                ids.forEach {
                    mgr.getDirection(it)?.let {
                        checkDirCanEnabled(it)
                    }
                }
            }

            mgr.upsertDirections(ids, isActive, profit, minSum, minSumCurrency, maxSum, maxSumCurrency)
            val dirNames = mgr.getDirectionNames(ids)
            val msg = "Массовое редактирование направлений: ${dirNames.joinToString(", ")}"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    delete("/directions") {
        runCatching {
            val ids = checkIds(call, "направления")
            val names = mgr.getDirectionNames(ids).joinToString(", ")
            ids.forEach {
                mgr.deleteDirection(it)
            }
            val msg = "Удалены направления: $names"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    suspend fun directionHandler(call: ApplicationCall, action: String, isEdit: Boolean = false) {
        val currencyMgr: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)
        val limitChecker: LimitChecker = KoinJavaComponent.get(LimitChecker::class.java)
        val alerts = mutableListOf<String>()

        val direction = call.receive<DirectionDTO>()
        if (direction.fromId == 0 || direction.toId == 0) {
            throw ValidationError("Коды валют не могут быть пустыми")
        }
        if(direction.fromId == direction.toId) {
            throw ValidationError("Коды валют не могут совпадать")
        }
        if (isEdit && direction.id == 0) {
            throw ValidationError("Не указан id направления для редактирования")
        }
        if(direction.formulaId == 0) {
            throw ValidationError("Не указана формула для курса")
        }
        if(direction.statusId == 0) {
            throw ValidationError("Не указан шаблон статуса")
        }
        // при откл валютах направл не включаем
        if(direction.isActive) {
            checkDirCanEnabled(direction.toDB())
        }
        val minCurrency = currencyMgr.getCurrencyById(direction.minSumCurrencyId) ?: throw ValidationError("Валюта min (id=${direction.minSumCurrencyId }) не найдена")
        val maxCurrency = currencyMgr.getCurrencyById(direction.maxSumCurrencyId) ?: throw ValidationError("Валюта max (id=${direction.maxSumCurrencyId }) не найдена")
        val give = currencyMgr.getCurrencyById(direction.fromId) ?: throw ValidationError("Валюта Отдаю (id=${direction.fromId }) не найдена")
        val get = currencyMgr.getCurrencyById(direction.toId) ?: throw ValidationError("Валюта Получаю (id=${direction.toId }) не найдена")
//        val oldDir = mgr.getDirection(direction.id)?.toDTO() ?: throw ValidationError("Направление обмена не найдено")

        // проверим лимиты на прием
        limitChecker.validateLimits(
            amountParam = direction.minSum,
            direction = direction,
            currencyCode = minCurrency.code,
            chainCode = minCurrency.payinChain,
            exchange = give.payin,
            isCheckDirGet = false,
            alerts = alerts
        )

        limitChecker.validateLimits(
            amountParam = direction.minSum,
            direction = direction,
            currencyCode = minCurrency.code,
            chainCode = minCurrency.payinChain,
            exchange = get.payout,
            isCheckDirGet = true,
            alerts = alerts
        )

        // проверим лимиты на отправку
        limitChecker.validateLimits(
            amountParam = direction.maxSum,
            direction = direction,
            currencyCode = maxCurrency.code,
            chainCode = maxCurrency.payoutChain,
            exchange = get.payout,
            isCheckDirGet = true,
            alerts = alerts
        )

        limitChecker.validateLimits(
            amountParam = direction.maxSum,
            direction = direction,
            currencyCode = maxCurrency.code,
            chainCode = maxCurrency.payoutChain,
            exchange = give.payin,
            isCheckDirGet = false,
            alerts = alerts
        )

        mgr.upsertDirection(direction)

        val answer = "${action} направление ${give.name} -> ${get.name}"
        log.withUser(call, (listOf(answer) + alerts).joinToString("\n"))
        call.respondApi(
            response = ApiResponse.Success(
                data = jsonMap("alerts" to alerts),
                message = answer
            ),
            statusCode = if(isEdit) HttpStatusCode.OK else HttpStatusCode.Created
        )
    }

    suspend fun orderStatusHandler(call: ApplicationCall, action: String, isEdit: Boolean = false) {
        val status = call.receive<StatusGroupDTO>()
        status.name = status.name.trim()
        if(status.name.isEmpty()) {
            throw ValidationError("Название групп статусов не может быть пустым")
        }
        if(status.idsFrom.isEmpty() || status.idsTo.isEmpty()) {
            throw ValidationError("Коды валют не могут быть пустыми")
        }
        if(isEdit && status.id == 0) {
            throw ValidationError("Не указан id группы статуса для редактирования")
        }
        mgr.upsertStatuses(status)

        val msg = "${action} группа статусов \"${status.name}\""
        log.withUser(call, msg)
        call.respondApi<Unit>(ApiResponse.Success(message = msg))
    }

    get("/direction/{id}") {
        runCatching {
            val id = call.parameters["id"]?.toInt() ?: throw ValidationError("Не указан id направления")
            val answer = mgr.getDirection(id)
            if(answer == null) throw ValidationError("Направление с id=$id не найдено")
            call.respondApi(ApiResponse.Success(data = answer.toDTO()))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/user/direction/{id}") {
        runCatching {
            val converter: ConvertPrice = KoinJavaComponent.get(ConvertPrice::class.java)
            val fieldManager: CurrencyFieldManager = KoinJavaComponent.get(CurrencyFieldManager::class.java)
            val fieldsGiveManager: CurrencyFieldsBindManager = KoinJavaComponent.get(
                CurrencyFieldsBindManager::class.java,
                named("giveFields")
            )
            val fieldsGetManager: CurrencyFieldsBindManager = KoinJavaComponent.get(
                CurrencyFieldsBindManager::class.java,
                named("getFields")
            )

            val id = call.parameters["id"]?.toInt() ?: throw ValidationError("Не указан id направления")
            val dir = mgr.getDirection(id)
            if(dir == null) throw ValidationError("Направление с id=$id не найдено")
            val minSumCurrency = currencyMgr.getCurrencyById(dir.minSumCurrencyId)?.code
                ?: throw ValidationError("Валюта для минимальной суммы не найдена")
            val maxSumCurrency = currencyMgr.getCurrencyById(dir.maxSumCurrencyId)?.code
                ?: throw ValidationError("Валюта для максимальной суммы не найдена")
            val fromCurrency = currencyMgr.getCurrencyById(dir.fromId)?.code
                ?: throw ValidationError("Валюта Отдаю не найдена")
            val toCurrency = currencyMgr.getCurrencyById(dir.toId)?.code
                ?: throw ValidationError("Валюта Получаю не найдена")
            var lastPrice = 0.0
            formulaMgr.getFormula(fromCurrency, toCurrency)?.let { f ->
                lastPrice = f.price * (dir.profit / 100 + 1)
            }
            val fields = fieldManager.getFields()
            val giveFieldsList = fields.filter { it.id in fieldsGiveManager.getFieldsByCurrency(dir.fromId).map { it.fieldId } }
            val getFieldsList = fields.filter { it.id in fieldsGetManager.getFieldsByCurrency(dir.toId).map { it.fieldId } }
            val answer = DirectionUserDTO(
                fromId = dir.fromId,
                toId = dir.toId,
                minSumGive = converter.convertAmount(dir.minSum, minSumCurrency, fromCurrency),
                maxSumGive = converter.convertAmount(dir.maxSum, maxSumCurrency, fromCurrency),
                minSumGet = converter.convertAmount(dir.minSum, minSumCurrency, toCurrency),
                maxSumGet = converter.convertAmount(dir.maxSum, maxSumCurrency, toCurrency),
                statusId = dir.statusTemplate.id,
                popup = mgr.getStatuses(dir.statusTemplate.id)
                    .firstOrNull { it.statusType == "popup" }?.text ?: "",
                price = lastPrice,
                giveFields = giveFieldsList.map { UserCurrencyFieldDTO(it.name, it.isRequired, it.hintAccountFrom) },
                getFields = getFieldsList.map { UserCurrencyFieldDTO(it.name, it.isRequired, it.hintAccountTo) }
            )
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    put("/direction") {
        runCatching {
            directionHandler(call, "Добавлено")
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/direction") {
        runCatching {
            directionHandler(call, "Изменено", true)
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/orderStatuses") {
        runCatching {
            val answer = mgr.getStatusTemplates().map {
                StatusShortDTO(it.id, it.caption, it.lastUpdated)
            }
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    delete("/orderStatuses") {
        runCatching {
            val ids = checkIds(call, "статусы")
            val names = mgr.getStatusTemplates()
                .filter { it.id in ids }
                .map { it.caption }
            ids.forEach {
                mgr.deleteStatusTemplate(it)
            }
            val msg = "Удалены статусы: $names"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/orderStatus/{id}") {
        runCatching {
            val id = call.parameters["id"]?.toInt() ?: throw ValidationError("Не указан id статуса")
            val statuses = mgr.getStatuses(id)
            if(statuses.isEmpty()) throw ValidationError("Статусов с id=$id не существует")
            val answer = StatusGroupDTO(
                statuses.first().template.id,
                mgr.getCurrenciesGiveForTemplateId(statuses.first().template.id) ?: emptySet<Int>(),
                mgr.getCurrenciesGetForTemplateId(statuses.first().template.id) ?: emptySet<Int>(),
                statuses.first().template.caption,
                statuses.map { StatusGroupDTO.Status(it.statusType, it.text) }
            )
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    put("/orderStatus") {
        runCatching {
            orderStatusHandler(call, "Добавлена")
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/orderStatus") {
        runCatching {
            orderStatusHandler(call, "Изменена", true)
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}

@Serializable
data class DirectionsAnswerDTO(
    val count: Int,
    val list: List<DirectionShortDTO>
)

@Serializable
data class DirectionsEditedAnswer(
    val message: String,
    val warnings: List<String>?
)

@Serializable
data class StatusShortDTO(
    val id: Int,
    val name: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastUpdated: LocalDateTime
)

// Задает группу статусов и сами статусы
@Serializable
data class StatusGroupDTO(
    val id: Int = 0,
    var idsFrom: Set<Int>,
    var idsTo: Set<Int>,
    var name: String,
    val list: List<Status>
) {
    @Serializable
    data class Status(
        var statusType: String,
        var text: String
    )
}

@Serializable
data class UserCurrencyFieldDTO(
    val name: String,
    val isRequired: Boolean,
    val hint: String
)