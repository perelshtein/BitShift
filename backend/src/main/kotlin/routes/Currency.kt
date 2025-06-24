package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.CurrencyValidator
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.ReserveFormatter
import com.github.perelshtein.ValidationError
import com.github.perelshtein.checkIds
import com.github.perelshtein.database.CurrencyManager
import com.github.perelshtein.database.CurrencyRecord
import com.github.perelshtein.database.CurrencyRecordDTO
import com.github.perelshtein.database.DirectionsManager
import com.github.perelshtein.database.Reserve
import com.github.perelshtein.database.toDB
import com.github.perelshtein.database.toDTO
import com.github.perelshtein.jsonMap
import com.github.perelshtein.respondApi
import com.github.perelshtein.trimmed
import com.github.perelshtein.withUser
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import kotlinx.serialization.json.JsonElement

fun Route.currencies() {
    val log = LoggerFactory.getLogger("Route.currencies")
    val currencyManager: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)
    val directionsManager: DirectionsManager = KoinJavaComponent.get(DirectionsManager::class.java)
    val validator: CurrencyValidator = KoinJavaComponent.get(CurrencyValidator::class.java)
    var reserveFmt: ReserveFormatter = KoinJavaComponent.get(ReserveFormatter::class.java)
    val customJson = Json {
        explicitNulls = false  // This will omit null values from the JSON output
    }

    get("/currencies") {
        val isOnlyGive = call.parameters["onlyGive"]?.toBoolean() ?: false
        val isOnlyGet = call.parameters["onlyGet"]?.toBoolean() ?: false
        var list = currencyManager.getCurrencies().map { it.toDTO() }
        if(isOnlyGive && isOnlyGet) throw ValidationError("Можно выбрать только один фильтр: onlyGive или onlyGet")
        if(isOnlyGive) list = list.filter { directionsManager.getDirections(count = 1, fromId = it.id, isActive = true).first > 0 }
        if(isOnlyGet) list = list.filter { directionsManager.getDirections(count = 1, toId = it.id, isActive = true).first > 0 }
        call.respondApi(ApiResponse.Success(data = list))
    }

    put("/currency") {
        runCatching {
            val body = call.receive<Map<String, JsonElement>>().trimmed()
            val currency = currencyManager.addCurrency(CurrencyRecord{
                this.name = body["name"]?.jsonPrimitive?.content ?: throw ValidationError("Имя валюты не указано")
                this.code = body["code"]?.jsonPrimitive?.content ?: throw ValidationError("Код валюты не заполнен. " +
                    "Он нужен для загрузки курса.")
                this.xmlCode = body["xmlCode"]?.jsonPrimitive?.content ?: throw ValidationError("XML-код валюты не заполнен. " +
                    "Он нужен для экспорта курса")
                this.fidelity = body["fidelity"]?.jsonPrimitive?.int ?: throw ValidationError("Точность валюты " +
                    "(число знаков после запятой) не указана")
                // эти поля не обязательны
                body["acctValidator"]?.jsonPrimitive?.content?.let { this.acctValidator = it }
                body["acctChain"]?.jsonPrimitive?.content?.let { this.acctChain = it }
                body["isEnabled"]?.jsonPrimitive?.content?.let { this.isEnabled = it.toBoolean() }
                body["payin"]?.jsonPrimitive?.content?.let { this.payin = it }
                body["payinCode"]?.jsonPrimitive?.content?.let { this.payinCode = it }
                body["payinChain"]?.jsonPrimitive?.content?.let { this.payinChain = it }
                body["payout"]?.jsonPrimitive?.content?.let { this.payout = it }
                body["payoutCode"]?.jsonPrimitive?.content?.let { this.payoutCode = it }
                body["payoutChain"]?.jsonPrimitive?.content?.let { this.payoutChain = it }
            })
            val resultLine = "Валюта ${currency.name} добавлена"
            log.withUser(call, resultLine)
            call.respondApi(
                response = ApiResponse.Success(
                    data = jsonMap("id" to currency.id),
                    message = resultLine
                ),
                statusCode = HttpStatusCode.Created
            )
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/currency") {
        runCatching {
            val body = call.receive<Map<String, JsonElement>>().trimmed()
            val id = body["id"]?.jsonPrimitive?.int ?: throw ValidationError("Идентификатор валюты не указан")

            val currency = currencyManager.getCurrencyById(id)?.apply {
                body["name"]?.jsonPrimitive?.content?.let { this.name = it }
                body["code"]?.jsonPrimitive?.content?.let { this.code = it }
                body["xmlCode"]?.jsonPrimitive?.content?.let { this.xmlCode = it }
                body["fidelity"]?.jsonPrimitive?.int?.let { this.fidelity = it }
                body["acctValidator"]?.jsonPrimitive?.content?.let { this.acctValidator = it }
                body["acctChain"]?.jsonPrimitive?.content?.let { this.acctChain = it }
                body["isEnabled"]?.jsonPrimitive?.content?.let { this.isEnabled = it.toBoolean() }
                body["payin"]?.jsonPrimitive?.content?.let { this.payin = it }
                body["payinCode"]?.jsonPrimitive?.content?.let { this.payinCode = it }
                body["payout"]?.jsonPrimitive?.content?.let { this.payout = it }
                body["payoutCode"]?.jsonPrimitive?.content?.let { this.payoutCode = it }
                body["payinChain"]?.jsonPrimitive?.content?.let { this.payinChain = it }
                body["payoutChain"]?.jsonPrimitive?.content?.let { this.payoutChain = it }
            } ?: throw ValidationError("Валюта с id=${id} не найдена")
            currencyManager.updateCurrency(currency)

            // если валюта отключена, направления тоже нужно отключить
            if(!currency.isEnabled) {
                val dirs = directionsManager.getDirections(fromId = currency.id, isActive = true).second +
                    directionsManager.getDirections(toId = currency.id, isActive = true).second
                if(dirs.isNotEmpty()) {
                    directionsManager.upsertDirections(dirs.map { it.id }, isEnable = false)
                    val dirNames = directionsManager.getDirectionNames(dirs.map { it.id })
                    log.withUser(call, "Направления отключены: ${dirNames.joinToString(", ")}")
                }
            }

            val msg = "Валюта ${currency.name} обновлена"
            log.withUser(call, msg)
            call.respondApi(
                response = ApiResponse.Success(
                    data = jsonMap("id" to currency.id),
                    message = msg
                )
            )
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/currencies/getValidators") {
        runCatching {
            call.respondApi(
                response = ApiResponse.Success(
                    data = validator.getSupportedCurrencies()
                )
            )
        }
        .getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/currency/{id}/validate/{address}") {
        runCatching {
            val id = call.parameters["id"]?.toInt() ?: throw ValidationError("id не указан")
            val address = call.parameters["address"] ?: throw ValidationError("Адрес для проверки не указан")
            val currency = currencyManager.getCurrencyById(id)
            val chain = currency?.acctChain ?: "AUTO"
            val validatorName = currency?.acctValidator ?: throw ValidationError("Валюта с id=$id не найдена")
            val currencyName = currency?.name ?: "id = ${currency?.id}"
            if(validatorName.isEmpty()) {
                call.respondApi<Unit>(ApiResponse.Warning(message = "Валидатор для валюты ${currencyName} не задан"))
                return@get
            }
            val answer = validator.validate(validatorName, chain, address)
            call.respondApi(
                response = ApiResponse.Success(
                    data = jsonMap("isPassed" to answer),
                    message = if(answer) "Адрес указан верно" else "Адрес неправильный"
                )
            )
        }
        .getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    delete("/currencies") {
        runCatching {
            val ids = checkIds(call, "валюты")
            val currencyNames = currencyManager.getCurrencyNames(ids).joinToString(", ")
            ids.forEach {
                currencyManager.deleteCurrency(it)
            }
            val msg = "Удалены валюты: $currencyNames"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/reserves") {
        val answer = currencyManager.getReserves().associate { reserve ->
            reserve.currency to runCatching {
                reserveFmt.calcReserve(reserve)
            }.getOrElse { exception ->
                log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
                ReserveResponse("error")
            }
        }
        //        val jsonResponse = customJson.encodeToString(answer)
        call.respondApi(ApiResponse.Success(data = answer))
    }

    get("/reserve/{currencyCode}") {
        runCatching {
            val currencyCode = call.parameters["currencyCode"] ?: throw ValidationError("Код валюты не указан")
            val reserve = currencyManager.getReserve(currencyCode)
            if(reserve == null) {
                val answer = ReserveResponse("off")
                call.respondApi(ApiResponse.Success(data = answer))
                return@get
            }

            val answer = reserveFmt.calcReserve(reserve)
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/reserve/{currencyCode}") {
        runCatching {
            saveReserve(call, "обновлен")
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/reserve/update/{currencyCode}") {
        runCatching {
            val currencyCode = call.parameters["currencyCode"] ?: throw ValidationError("Код валюты не указан")
            val updated = reserveFmt.loadFromFile(currencyCode)
            currencyManager.upsertReserve(Reserve {
                this.currency = currencyCode
                this.reserveType = "fromFile"
                this.value = updated.value ?: throw ValidationError("Поле value в файле reserve.json не может быть пустым")
                this.reserveCurrency = updated.reserveCurrency ?: throw ValidationError("Поле reserveCurrency в файле reserve.json не может быть пустым")
            })
            val msg = "Резерв из файла для ${currencyCode} обновлен вручную: ${updated.value} ${updated.reserveCurrency}"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/user/reserve") {
        runCatching {
            val converter: ConvertPrice = KoinJavaComponent.get(ConvertPrice::class.java)
//            val from = call.parameters["from"] ?: throw ValidationError("Код валюты Отдаю не указан")
            val to = call.parameters["to"] ?: throw ValidationError("Код валюты Получаю не указан")

            val reserve = currencyManager.getReserve(to)
            if (reserve == null || reserve.value == 0.0) {
                call.respondApi(
                    response = ApiResponse.Success(
                        data = jsonMap("reserve" to 0.0)
                    )
                )
                return@get
            }

            val answer = converter.convertAmount(reserve.value, reserve.reserveCurrency, to)
            call.respondApi(
                response = ApiResponse.Success(
                    data = jsonMap("reserve" to answer)
                )
            )

        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}

suspend fun saveReserve(call: ApplicationCall, message: String) {
    val log = LoggerFactory.getLogger("saveReserve")
    val currencyManager: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)
    var reserveFmt: ReserveFormatter = KoinJavaComponent.get(ReserveFormatter::class.java)

    val currencyCode = call.parameters["currencyCode"] ?: throw ValidationError("Код валюты не указан")
    val body = call.receive<Map<String, JsonElement>>()
    val allowReserveTypes = listOf("fromFile", "fromExchange", "off", "manual")
    val allowExchanges = listOf("Bybit", "Mexc")

    val reserve = Reserve {
        this.currency = currencyCode

        body["reserveType"]?.let { this.reserveType = it.toString().trim('"', ' ') }
        if(!allowReserveTypes.contains(this.reserveType)) throw ValidationError("Недопустимый тип резерва: ${this.reserveType}")

        //загрузим резерв из файла и сохраним его в базу
        if(reserveType == "fromFile") {
            reserveFmt.loadFromFile(currencyCode).let {
                this.value = it.value ?: 0.0
                this.reserveCurrency = it.reserveCurrency ?: throw ValidationError("Поле reserveCurrency в файле reserve.json не может быть пустым")
            }
        }

        if(reserveType == "manual") {
            body["reserveCurrency"]?.let {
                this.reserveCurrency = it.toString().trim('"', ' ')
                if (this.reserveCurrency.isEmpty()) throw ValidationError("Код валюты, в которой задается резерв, не может быть пустым")
            }

            body["value"]?.let {
                this.value = it.toString().toDouble()
                if (this.value < 0.0) throw ValidationError("Резерв не может быть отрицательным")
            }
            if (this.reserveType == "off" || this.reserveType == "fromExchange") this.value = 0.0
        }

        if(reserveType == "fromExchange") {
            body["exchangeName"]?.let {
                this.exchangeName = it.toString().trim('"', ' ')
                if (!allowExchanges.contains(this.exchangeName)) {
                    throw ValidationError("Биржа не поддерживается: ${this.exchangeName}")
                }
            }
            this.reserveCurrency = "USDT"
        }
    }
    currencyManager.upsertReserve(reserve)

    val msg = "Резерв для валюты $currencyCode ${message}"
    log.withUser(call, msg)
    call.respondApi<Unit>(ApiResponse.Success(message = msg))
}

@Serializable
data class CurrencyEdited(
    val id: Int,
    val message: String
)

@Serializable
data class ReserveResponse(
    val reserveType: String,
    val reserveCurrency: String? = null,
    val value: Double? = null,
    val exchangeName: String? = null
)