package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.ValidationError
import com.github.perelshtein.checkIds
import com.github.perelshtein.database.CurrencyFieldDTO
import com.github.perelshtein.database.CurrencyFieldManager
import com.github.perelshtein.database.CurrencyFieldsBindManager
import com.github.perelshtein.database.CurrencyManager
import com.github.perelshtein.database.toDB
import com.github.perelshtein.database.toDTO
import com.github.perelshtein.jsonMap
import com.github.perelshtein.respondApi
import com.github.perelshtein.withUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.post
import io.ktor.server.routing.delete
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.Serializable
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

fun Route.currencyFields() {
    val log = LoggerFactory.getLogger("Route.currencyFields")
    val fieldManager: CurrencyFieldManager = KoinJavaComponent.get(CurrencyFieldManager::class.java)
    val fieldsGiveManager: CurrencyFieldsBindManager = KoinJavaComponent.get(
        CurrencyFieldsBindManager::class.java,
        named("giveFields")
    )
    val fieldsGetManager: CurrencyFieldsBindManager = KoinJavaComponent.get(
        CurrencyFieldsBindManager::class.java,
        named("getFields")
    )
    val currencyManager: CurrencyManager = KoinJavaComponent.get(CurrencyManager::class.java)

    // загружаем все доп.поля, без привязки к валютам
    get("/currencyFields") {
        val answer = fieldManager.getFields()
        call.respondApi(
            ApiResponse.Success(data = answer.map { it.toDTO() })
        )
    }

    delete("/currencyFields") {
        runCatching {
            val ids = checkIds(call, "поля")
            val names = fieldManager.getFieldCaption(ids).joinToString(", ")
            ids.forEach {
                fieldManager.delete(it)
            }
            val resultLine = if (ids.size > 1) "Поля ${names} удалены" else "Поле ${names} удалено"
            log.withUser(call, resultLine)
            call.respondApi<Unit>(ApiResponse.Success(message = resultLine))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    fun checkField(field: CurrencyFieldDTO) {
        if (field.name.trim().isEmpty()) throw ValidationError("Название поля не указано")
    }

    put("/currencyField") {
        runCatching {
            val field = call.receive<CurrencyFieldDTO>()
            checkField(field)
            val newField = fieldManager.add(field.toDB())
            val resultLine = "Поле ${newField.name} добавлено"
            log.withUser(call, resultLine)
            call.respondApi(
                response = ApiResponse.Success(
                    data = jsonMap("id" to newField.id),
                    message = resultLine
                ),
                statusCode = HttpStatusCode.Created
            )
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/currencyField") {
        runCatching {
            val field = call.receive<CurrencyFieldDTO>()
            checkField(field)
            fieldManager.update(field.toDB())
            val resultLine = "Поле ${field.name} обновлено"
            log.withUser(call, resultLine)
            call.respondApi(ApiResponse.Success(
                message = resultLine,
                data = jsonMap("id" to field.id)
            ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    //загрузка списка валют для конкретного поля
    get("/currencyField/{id}/currencies") {
        runCatching {
            val id = call.parameters["id"]?.toIntOrNull() ?: throw ValidationError("Загрузка валют Отдаю и Получаю для поля: id поля не указано")
            val giveCurrencies = fieldsGiveManager.getCurrenciesByField(id)
            val getCurrencies = fieldsGetManager.getCurrenciesByField(id)
            val answer = mapOf("give" to giveCurrencies, "get" to getCurrencies)
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    //установка списка валют для конкретного поля
    post("/currencyField/{id}/currencies") {
        runCatching {
            val id = call.parameters["id"]?.toIntOrNull() ?: throw ValidationError("Установка валют Отдаю и Получаю для поля: id поля не указано")
            val fieldName = fieldManager.getFieldCaption(listOf(id)).firstOrNull() ?:  "id = $id"
            log.withUser(call, "Установка валют Отдаю и Получаю для поля: ${fieldName}")
            val body = call.receive<CurrenciesForField>()
            fieldsGiveManager.setCurrenciesByField(id, body.give)
            fieldsGetManager.setCurrenciesByField(id, body.get)
            call.respondApi<Unit>(ApiResponse.Success(message = "Валюты для поля ${fieldName} заданы успешно."))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    //загрузка полей Отдаю и Получаю для валюты
    get("/currency/{id}/fields") {
        runCatching {
            val id = call.parameters["id"]?.toIntOrNull() ?: throw ValidationError("Загрузка доп.полей: id валюты не указано")
            val giveFields = fieldsGiveManager.getFieldsByCurrency(id).map { it.fieldId }
            val getFields = fieldsGetManager.getFieldsByCurrency(id).map { it.fieldId }
            val answer = mapOf("give" to giveFields, "get" to getFields)
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    //установка полей Отдаю и Получаю для валюты
    post("/currency/{id}/fields") {
        runCatching {
            val id = call.parameters["id"]?.toIntOrNull() ?: throw ValidationError("Установка доп.полей: id валюты не указано")
            val currencyName = currencyManager.getCurrencyNames(listOf(id)).firstOrNull() ?:  "id = $id"
            val body = call.receive<CurrenciesForField>()
            fieldsGiveManager.setFieldsByCurrency(id, body.give)
            fieldsGetManager.setFieldsByCurrency(id, body.get)
            log.withUser(call, "Установка доп.полей: ${currencyName}")
            call.respondApi<Unit>(ApiResponse.Success(message = "Доп.поля для валюты ${currencyName} заданы успешно."))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}

@Serializable
data class CurrencyFieldEdited(
    val id: Int,
    val message: String
)

@Serializable
data class CurrenciesForField(
    val give: List<Int>,
    val get: List<Int>
)