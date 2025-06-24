package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.Pagination
import com.github.perelshtein.ValidationError
import com.github.perelshtein.checkURL
import com.github.perelshtein.database.ApiKey
import com.github.perelshtein.database.CourseDTO
import com.github.perelshtein.database.CourseManager
import com.github.perelshtein.database.ExchangeManager
import com.github.perelshtein.database.ExchangeRecordDTO
import com.github.perelshtein.database.toDTO
import com.github.perelshtein.exchanges.UpdateCourses
import com.github.perelshtein.respondApi
import com.github.perelshtein.withUser
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import org.slf4j.LoggerFactory
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.koin.java.KoinJavaComponent
import kotlin.getOrElse

fun Route.exchanges() {
    val log = LoggerFactory.getLogger("Route.exchanges")
    val mgr: ExchangeManager = KoinJavaComponent.get(ExchangeManager::class.java)

    get("/exchanges/{name}") {
        runCatching {
            val name = call.parameters["name"]?.toString() ?: throw ValidationError("Имя биржи не указано")
            val exchange = mgr.getExchangeByName(name)
            if (exchange == null) throw ValidationError("Биржа ${name} не найдена")
            call.respondApi(ApiResponse.Success(data = exchange.toDTO()))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/exchanges/{name}") {
        runCatching {
            val name = call.parameters["name"]?.toString() ?: throw ValidationError("Имя биржи не указано")
            val exchange = call.receive<ExchangeRecordDTO>()
            val oldExchange = mgr.getExchangeByName(name)
            if (oldExchange == null) throw ValidationError("Биржа \"$name\" не найдена")
            if (exchange.updatePeriod <= 0) throw ValidationError("Период обновления должен быть > 0")
            if (exchange.url.isEmpty()) throw ValidationError("URL биржи не указан")
            if (!exchange.url.checkURL()) throw ValidationError("URL биржи указан неправильно")
            mgr.updateExchange(oldExchange.apply {
                this.isEnabled = exchange.isEnabled
                this.url = exchange.url
                this.updatePeriod = exchange.updatePeriod
                this.blacklist = Json.encodeToString(exchange.blacklist)
                this.maxFailCount = exchange.maxFailCount
            })
            if (exchange.apiKey != "******" || exchange.secretKey != "******") {
                mgr.setApiKeys(oldExchange.id, ApiKey {
                    this.apiKey = exchange.apiKey
                    this.secretKey = exchange.secretKey
                })
                log.withUser(call, "Изменен секретный ключ биржи ${name}")
            }
            val msg = "Настройки биржи ${name} обновлены"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    // Обновить курсы прямо сейчас!
    post("/exchanges/{name}/courses/update") {
        runCatching {
            val courses: UpdateCourses = KoinJavaComponent.get(UpdateCourses::class.java)
            val name = call.parameters["name"]?.toString() ?: throw ValidationError("Имя биржи не указано")
            val id = mgr.getExchangeByName(name)?.id ?: throw ValidationError("Биржа ${name} не найдена")
            courses.update(id)
            call.respondApi<Unit>(ApiResponse.Success(message = "Курсы для биржи ${name} обновлены"))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/courses") {
        runCatching {
            val courseMgr: CourseManager = KoinJavaComponent.get(CourseManager::class.java)
            val exchange = call.parameters["exchange"]?.toString()
            val filter = call.parameters["filter"]?.toString()
            val start = call.parameters["start"]?.toInt() ?: 0
            val count = call.parameters["count"]?.toInt() ?: 100
            val found = courseMgr.getCourses(start, count, filter, exchange)
            call.respondApi(
                ApiResponse.Success(
                    data = Pagination(
                        items = found.second.map { it.toDTO() },
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
}

@Serializable
data class CoursesAnswer(
    val count: Int,
    val list: List<CourseDTO>
)