package com.github.perelshtein.routes

import com.github.perelshtein.AccessControl
import com.github.perelshtein.ApiResponse
import com.github.perelshtein.BONUS_TYPE
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.OptionsManager
import com.github.perelshtein.Pagination
import com.github.perelshtein.User
import com.github.perelshtein.UserDTO
import com.github.perelshtein.ValidationError
import com.github.perelshtein.checkEmail
import com.github.perelshtein.checkIds
import com.github.perelshtein.database.OrdersManager
import com.github.perelshtein.generateHash
import com.github.perelshtein.getUserId
import com.github.perelshtein.respondApi
import com.github.perelshtein.toDB
import com.github.perelshtein.toDto
import com.github.perelshtein.trimmed
import com.github.perelshtein.withUser
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import org.koin.java.KoinJavaComponent
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

fun Route.users() {
    val log = LoggerFactory.getLogger("Route.users")
    val accessControl: AccessControl = KoinJavaComponent.get(AccessControl::class.java)

    get("/users/{id}") {
        runCatching {
            val id = call.parameters["id"]?.toIntOrNull()
            if(id == null) throw ValidationError("Не указан идентификатор пользователя")
            log.withUser(call, "Запрос пользователя с id=$id", LOG_TYPE.DEBUG)
            val user = accessControl.getUserById(id)
            if(user == null) throw ValidationError("Пользователь с id=$id не найден")
            call.respondApi(ApiResponse.Success( data = user.toDto()))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/users") {
        runCatching {
            val offset = call.parameters["start"]?.toIntOrNull() ?: 0
            val count = call.parameters["count"]?.toIntOrNull() ?: 10
            val roleId = call.parameters["roleId"]?.toIntOrNull()
            val queryStr = call.parameters["query"].orEmpty()
            val orderMgr: OrdersManager = KoinJavaComponent.get(OrdersManager::class.java)
            log.withUser(call, "Запрос списка пользователей", LOG_TYPE.DEBUG)
            val answer = accessControl.getUsers(from = offset, count = count, role = roleId, query = queryStr)
                .let {
                    it.first to it.second.map {
                        it.toDto().apply { this.ordersCount = orderMgr.getCountCompleted(it.id) }
                    }
                }
            call.respondApi(ApiResponse.Success(
                data = Pagination(
                    items = answer.second,
                    total = answer.first,
                    page = offset / count + 1,
                    pageSize = count
                )
            ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    fun checkUserFields(userDto: UserDTO) {
        if(userDto.name.trim().isEmpty()) throw ValidationError("Имя не может быть пустым")
        if(userDto.password.trim().isEmpty()) throw ValidationError("Пароль не может быть пустым")
        if(userDto.mail.trim().isEmpty()) throw ValidationError("Поле Почта необходимо заполнить")
        if(!userDto.mail.checkEmail()) throw ValidationError("Адрес почты указан в неправильном формате")
    }

    //Добавляем нового пользователя
    put("/users") {
        runCatching {
            val userDto = call.receive<UserDTO>()
            checkUserFields(userDto)

            val newUser = userDto.toDB().apply {
                password = password.generateHash()
            }
            accessControl.addUser(newUser)
            val resultLine = "Добавлен новый пользователь: ${newUser.name} (${newUser.mail})"
            log.withUser(call, resultLine)
            call.respondApi<Unit>(ApiResponse.Success(message = resultLine))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    //Редактируем существующего пользователя
    post("/users") {
        runCatching {
            val userDto = call.receive<UserDTO>()
            if(userDto.id == null) throw ValidationError("Не указан id пользователя")
            checkUserFields(userDto)
            val oldUser = accessControl.getUserById(userDto.id)
            if(oldUser == null) throw ValidationError("Пользователь с id=${userDto.id} не найден")
            val newUser = oldUser.apply {
                name = userDto.name
                if(userDto.password != "******") password = userDto.password.generateHash()
                mail = userDto.mail
                cashbackPercent = userDto.cashbackPercent
                cashbackType = userDto.cashbackType.value
                referralPercent = userDto.referralPercent
                referralType = userDto.referralType.value
                roleId = userDto.roleId
                referralId = userDto.referralId
            }
            accessControl.updateUser(newUser)
            val resultLine = "Пользователь ${newUser.name} сохранен"
            log.withUser(call, resultLine)
            call.respondApi<Unit>(ApiResponse.Success(message = resultLine))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    delete("/users") {
        runCatching {
            val ids = checkIds(call, "пользователи")
            val userNames = ids.map { accessControl.getUserById(it)?.name }.joinToString(", ")
            ids.forEach {
                accessControl.deleteUser(it)
            }
            val resultLine = "Удалены пользователи: $userNames"
            log.withUser(call, resultLine)
            call.respondApi<Unit>(ApiResponse.Success(message = resultLine))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    // Маршруты для сайта
    // загрузка инф об активном пользователе
    get("/user") {
        runCatching {
            val optionsMgr: OptionsManager = KoinJavaComponent.get(OptionsManager::class.java)
            val id = call.getUserId() ?: throw ValidationError("Пользователь не авторизован")
            log.withUser(call, "Запрос инф о пользователе", LOG_TYPE.DEBUG)
            val user = accessControl.getUserById(id)
            if(user == null) throw ValidationError("Пользователь с id=$id не найден")
            val answer = UserSelfDTO(
                user.id,
                user.date,
                user.name,
                "******",
                user.mail,
                user.roleId,
                user.referralPercent ?: optionsMgr.getOptions().referralPercent,
                BONUS_TYPE.fromValue(user.referralType),
                user.cashbackPercent ?: optionsMgr.getOptions().cashbackPercent,
                BONUS_TYPE.fromValue(user.cashbackType),
                accessControl.getRoleById(user.roleId)?.isAdminPanel ?: false
            )
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/user") {
        runCatching {
            val id = call.getUserId() ?: throw ValidationError("Пользователь не авторизован")
            log.withUser(call, "Обновление инф о пользователе", LOG_TYPE.DEBUG)
            val user = accessControl.getUserById(id)
            if(user == null) throw ValidationError("Пользователь с id=$id не найден")

            val body = call.receive<Map<String, JsonElement>>().trimmed()
            body["name"]?.jsonPrimitive?.content?.let {
                user.name = it
            }
            body["mail"]?.jsonPrimitive?.content?.let {
                user.mail = it
            }
            body["password"]?.jsonPrimitive?.content?.let {
                if(it != "******") user.password = it.generateHash()

            }
            body["cashbackPercent"]?.jsonPrimitive?.content?.toDoubleOrNull()?.let {
                user.cashbackPercent = it
            }
            body["referralPercent"]?.jsonPrimitive?.content?.toDoubleOrNull()?.let {
                user.referralPercent = it
            }
            body["referralType"]?.jsonPrimitive?.content?.let {
                user.referralType = it
            }
            body["cashbackType"]?.jsonPrimitive?.content?.let {
                user.cashbackType = it
            }
            accessControl.updateUser(user)
            val resultLine = "Инф о пользователе обновлена"
            log.withUser(call, resultLine)
            call.respondApi<Unit>(ApiResponse.Success(message = resultLine))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}

//инф пользов о себе
@Serializable
data class UserSelfDTO(
    val id: Int = 0,
    @Serializable(with = LocalDateTimeSerializer::class)
    val date: LocalDateTime = LocalDateTime.now(),
    var name: String = "",
    var password: String = "",
    var mail: String = "",
    var roleId: Int = 0,
    var referralPercent: Double = 0.0,
    var referralType: BONUS_TYPE,
    var cashbackPercent: Double = 0.0,
    val cashbackType: BONUS_TYPE,
    var isAdminPanel: Boolean
)

