package com.github.perelshtein.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.github.perelshtein.ACTION
import com.github.perelshtein.AccessControl
import com.github.perelshtein.ApiResponse
import com.github.perelshtein.CookieGen
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.SessionManager
import com.github.perelshtein.respondApi
import com.github.perelshtein.withUser
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory
import kotlin.text.toCharArray

fun Route.login() {
    // вход в админку
    post("/login") {
        val log = LoggerFactory.getLogger("Route.login")
        val access: AccessControl = get(AccessControl::class.java)
        val sessionManager: SessionManager = get(SessionManager::class.java)
        val cookieGen: CookieGen = get(CookieGen::class.java)
        val credentials = call.receive<Map<String, String>>()

        runCatching {
            val username = credentials["username"] ?: throw LoginError("Почта не указана")
            val password = credentials["password"] ?: throw LoginError("Пользователь ${username}: пароль не указан")

            val user = access.getUserByMail(username)
            if (user == null) throw LoginError("Неправильная почта или пароль", "Пользователь \"${username}\" не найден в базе")
            val role = access.getRoleById(user.roleId)
            if (role == null) throw LoginError(
                "Неправильная почта или пароль",
                "Роль для пользователя ${username} не найдена в базе"
            )

            val passwordMatches = BCrypt.verifyer().verify(password.toCharArray(), user.password).verified
            if (!passwordMatches) throw LoginError(
                "Неправильная почта или пароль",
                "Пользователь ${username}: пароль не подходит"
            )

            // Обновим время последнего входа или создадим новую сессию
            val existingSession = sessionManager.getSessionByUserId(user.id)
            val token = if (existingSession != null && sessionManager.validateSession(existingSession.token)) {
                existingSession.token
            } else {
                sessionManager.createSession(user.id)
            }

            cookieGen.generate(call, token)
            log.withUser(call, "Успешный вход")
            call.respondApi(ApiResponse.Success(
                data = LoginInfo(
                    userId = user.id,
                    roleId = user.roleId
                ),
                message = if(role.isAdminPanel) "Приветствуем вас в панели управления." else "Приветствуем вас в ЛК на сайте.",
                action = if(role.isAdminPanel) ACTION.ADMIN else ACTION.WEBSITE
            ))
        }.getOrElse {
            if(it is LoginError) {
                val logText = it.logInfo ?: it.message ?: "Неизвестная ошибка"
                log.withUser(call, logText, LOG_TYPE.WARN)
            }
            throw it // т.к. код Unauthorized будет перехвачен в Ktor, отправим исключение ему
        }
    }
}

class LoginError(message: String, val logInfo: String? = null) : Exception(message)

@Serializable
data class LoginInfo(
    var userId: Int,
    var roleId: Int
)