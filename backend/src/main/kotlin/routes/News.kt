package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.Pagination
import com.github.perelshtein.ValidationError
import com.github.perelshtein.checkIds
import com.github.perelshtein.database.NewsManager
import com.github.perelshtein.database.NewsRecordDTO
import com.github.perelshtein.database.toDB
import com.github.perelshtein.database.toDTO
import com.github.perelshtein.respondApi
import com.github.perelshtein.withUser
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.util.reflect.typeInfo
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

fun Route.news() {
    val log = LoggerFactory.getLogger("Route.news")
    val newsManager: NewsManager = KoinJavaComponent.get(NewsManager::class.java)

    get("/news") {
        runCatching {
            val start = call.parameters["start"]?.toIntOrNull() ?: 0
            val count = call.parameters["count"]?.toIntOrNull() ?: 10
            val textSize = call.parameters["textSize"]?.toIntOrNull() ?: 2048
            log.withUser(call, "Запрос списка новостей", LOG_TYPE.DEBUG)
            val found = newsManager.getNews(start, count, textSize)
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

    get("/news/{id}") {
        runCatching {
            val id = call.parameters["id"]?.toIntOrNull()
            if(id == null) throw ValidationError("Не указан идентификатор новости")
            log.withUser(call, "Запрос новости с id=$id", LOG_TYPE.DEBUG)
            val newsRecord = newsManager.getNewsRecord(id)
            if(newsRecord == null) throw ValidationError("Новость с id=$id не найдена")
            call.respondApi(ApiResponse.Success(
                data = newsRecord.toDTO())
            )
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    put("/news") {
        runCatching {
            val newsRecord = call.receive<NewsRecordDTO>()
            if(newsRecord.caption.isEmpty()) throw ValidationError("Название новости не может быть пустым")
            newsManager.addNews(newsRecord.toDB())
            val msg = "Новость ${newsRecord.caption} добавлена"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/news") {
        runCatching {
            val newsRecord = call.receive<NewsRecordDTO>()
            if(newsRecord.caption.isEmpty()) throw ValidationError("Название новости не может быть пустым")
            val oldNewsRecord = newsManager.getNewsRecord(newsRecord.id)
            if(oldNewsRecord == null) throw ValidationError("Новость с id=${newsRecord.id} не найдена")
            newsManager.updateNews(oldNewsRecord.apply {
                caption = newsRecord.caption
                text = newsRecord.text
            })
            val msg = "Новость ${newsRecord.caption} обновлена"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))

        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    delete("/news") {
        runCatching {
            val ids = checkIds(call, "новости")
            val newsCaptionList = newsManager.getNewsCaption(ids)
            ids.forEach {
                newsManager.deleteNews(it)
            }
            val msg =  if(ids.size == 1) "Новость ${newsCaptionList.joinToString(";")} удалена" else
            "Новости ${newsCaptionList.joinToString(";")} удалены"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}
