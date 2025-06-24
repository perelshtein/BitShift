package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.COOKIE_AUTH_TOKEN
import com.github.perelshtein.CookieGen
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.SessionManager
import com.github.perelshtein.respondApi
import com.github.perelshtein.withUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.response.respondText
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

fun Route.logout() {
    get("/logout") {
        val sessionManager: SessionManager = KoinJavaComponent.get(SessionManager::class.java)
        val cookieGen: CookieGen = KoinJavaComponent.get(CookieGen::class.java)
        val log = LoggerFactory.getLogger("Route.logout")
        runCatching {
            val token = call.request.cookies.get(COOKIE_AUTH_TOKEN)
            if(token == null || !sessionManager.isSessionExists(token)) {
                val msg = "Сессия уже была закрыта ранее."
                log.withUser(call, msg)
                call.respondApi<Unit>(ApiResponse.Warning(message = msg))
                return@get
            }
            log.withUser(call, "Пользователь закрыл сессию")
            cookieGen.delete(call)
            sessionManager.delete(token)
            call.respondApi<Unit>(ApiResponse.Success(message = "Сессия закрыта. Вы вышли."))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw Exception(exception.message)
        }
    }
}