package com.github.perelshtein.routes

import com.github.perelshtein.AccessControl
import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.RoleDTO
import com.github.perelshtein.ValidationError
import com.github.perelshtein.checkIds
import com.github.perelshtein.jsonMap
import com.github.perelshtein.respondApi
import com.github.perelshtein.toDB
import com.github.perelshtein.toDto
import com.github.perelshtein.withUser
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

fun Route.roles() {
    val log = LoggerFactory.getLogger("Route.roles")
    val accessControl: AccessControl = KoinJavaComponent.get(AccessControl::class.java)

    get("/roles") {
        runCatching {
            log.withUser(call, "Запрос списка ролей", LOG_TYPE.DEBUG)
            val roles = accessControl.getRoles().map{ it.toDto()}
            call.respondApi(ApiResponse.Success(data = roles))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    put("/roles") {
        runCatching {
            val roleDto = call.receive<RoleDTO>()
            if(roleDto.name.trim().isEmpty()) throw ValidationError("Имя не может быть пустым")
            val newId = accessControl.addRole(roleDto.toDB())
            val resultLine = "Новая роль ${roleDto.name} добавлена"
            log.withUser(call, resultLine)
            call.respondApi(ApiResponse.Success(
                message = resultLine,
                data = jsonMap("id" to newId)
            ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/roles") {
        runCatching {
            val roleDto = call.receive<RoleDTO>()
            if(roleDto.name.trim().isEmpty()) throw ValidationError("Имя не может быть пустым")
            val oldRole = accessControl.getRoleById(roleDto.id)
            if(oldRole == null) throw ValidationError("Роль \"${roleDto.name.trim()}\" (id=${roleDto.id}) не найдена")
            val newRole = oldRole.apply {
                name = roleDto.name
                isAdminPanel = roleDto.isAdminPanel
                isEditUserAndRole = roleDto.isEditUserAndRole
                isEditNews = roleDto.isEditNews
                isEditCurrency = roleDto.isEditCurrency
                isEditOptions = roleDto.isEditOptions
                isEditDirection = roleDto.isEditDirection
                isEditReserve = roleDto.isEditReserve
                isEditNotify = roleDto.isEditNotify
                isEditReview = roleDto.isEditReview
                isSendReferralPayouts = roleDto.isSendReferralPayouts
            }
            accessControl.updateRole(newRole)
            val resultLine = "Роль ${newRole.name} сохранена"
            log.withUser(call, resultLine)
            call.respondApi(ApiResponse.Success(message = resultLine, data = jsonMap("id" to newRole.id)))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    delete("/roles") {
        runCatching {
            val ids = checkIds(call, "роли")
            val roleNames = accessControl.getRoles().filter { it.id in ids }.map { it.name }.joinToString(", ")
            ids.forEach {
                accessControl.deleteRole(it)
            }
            val resultLine = "Удалены роли: $roleNames"
            log.withUser(call, resultLine)
            call.respondApi<Unit>(ApiResponse.Success(message = resultLine))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}