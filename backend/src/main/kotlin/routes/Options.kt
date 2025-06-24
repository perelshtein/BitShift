package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.OptionDTO
import com.github.perelshtein.OptionsManager
import com.github.perelshtein.jsonMap
import com.github.perelshtein.respondApi
import com.github.perelshtein.toDB
import com.github.perelshtein.toDTO
import com.github.perelshtein.withUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.util.reflect.typeInfo
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

fun Route.options() {
    val log = LoggerFactory.getLogger("Route.options")

    get("/options") {
        val optionsManager: OptionsManager = org.koin.java.KoinJavaComponent.get(OptionsManager::class.java)
        runCatching {
            log.withUser(call, "Запрос настроек")
            val answer = optionsManager.getOptions()
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/maintenance") {
        val optionsManager: OptionsManager = org.koin.java.KoinJavaComponent.get(OptionsManager::class.java)
        runCatching {
            val answer = optionsManager.getOptions()
            call.respondApi(ApiResponse.Success(data = jsonMap("isMaintenance" to answer.isMaintenance)))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/options") {
        val optionsManager: OptionsManager = org.koin.java.KoinJavaComponent.get(OptionsManager::class.java)
        runCatching {
            val optionsDto = call.receive<OptionDTO>()
            optionsManager.setOptions(optionsDto)
            val msg = "Настройки сохранены"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}