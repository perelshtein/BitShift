package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.NotifyBindManager
import com.github.perelshtein.database.NotifyDTO
import com.github.perelshtein.respondApi
import com.github.perelshtein.withUser
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.reflect.typeInfo
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

fun Route.notify() {
    val log = LoggerFactory.getLogger("Route.notify")
    val notifyUserMgr: NotifyBindManager = KoinJavaComponent.get(NotifyBindManager::class.java, named("user"))
    val notifyAdminMgr: NotifyBindManager = KoinJavaComponent.get(NotifyBindManager::class.java, named("admin"))

    suspend fun getNotify(call: ApplicationCall, mgr: NotifyBindManager, msgTitle: String) {
        runCatching {
            log.withUser(call, "Запрос списка уведомлений для ${msgTitle}", LOG_TYPE.DEBUG)
            val result = mgr.getStatuses().associate {
                it.key to NotifyFrontendDTO(
                    subject = it.subject,
                    text = it.text
                )
            }
            call.respondApi(ApiResponse.Success(data = result))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    suspend fun postNotify(call: ApplicationCall, mgr: NotifyBindManager, msgTitle: String) {
        runCatching {
            val notify = call.receive<Map<String, NotifyFrontendDTO>>()
            if(notify.any { it.key.isBlank() }) {
                throw ValidationError("Не указано название (ключ) уведомления")
            }
            log.withUser(call, "Редактирование уведомлений для ${msgTitle}", LOG_TYPE.DEBUG)
            val notifyToSave = notify.map {
                NotifyDTO(
                    key = it.key,
                    subject = it.value.subject,
                    text = it.value.text
                )
            }
            mgr.upsertStatuses(notifyToSave)
            call.respondApi<Unit>(ApiResponse.Success(message = "Уведомления обновлены"))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/notify/users") {
        getNotify(call, notifyUserMgr, "пользователей")
    }

    get("/notify/admin") {
        getNotify(call, notifyAdminMgr, "администраторов")
    }

    post ("/notify/users") {
        postNotify(call, notifyUserMgr, "пользователей")
    }

    post ("/notify/admin") {
        postNotify(call, notifyAdminMgr, "администраторов")
    }
}

@Serializable
data class NotifyFrontendDTO(
    val subject: String,
    val text: String
)