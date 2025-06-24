package com.github.perelshtein.routes

import com.github.perelshtein.AccessControl
import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.Pagination
import com.github.perelshtein.ValidationError
import com.github.perelshtein.checkIds
import com.github.perelshtein.database.ReviewsAdminDTO
import com.github.perelshtein.database.ReviewsManager
import com.github.perelshtein.database.ReviewsPublicDTO
import com.github.perelshtein.database.ReviewsUserDTO
import com.github.perelshtein.database.toAdminDTO
import com.github.perelshtein.database.toPublicDTO
import com.github.perelshtein.database.toUserDTO
import com.github.perelshtein.getUserId
import com.github.perelshtein.jsonMap
import com.github.perelshtein.respondApi
import com.github.perelshtein.trimmed
import com.github.perelshtein.withUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

fun Route.reviews() {
    val log = LoggerFactory.getLogger("Route.reviews")
    val mgr by KoinJavaComponent.inject<ReviewsManager>(ReviewsManager::class.java)
    val accessControl by KoinJavaComponent.inject<AccessControl>(AccessControl::class.java)

    fun checkStopWords(text: String) {
        val stopWords = mgr.getStopWords().filter {
            text.contains(it, ignoreCase = true)
        }
        if(stopWords.isNotEmpty()) {
            throw ValidationError("Текст содержит запрещенные слова: ${stopWords.joinToString(", ")}")
        }
    }

    get("/publicReviews") {
        runCatching {
            val _start = call.parameters["start"]?.toIntOrNull() ?: 0
            val _count = call.parameters["count"]?.toIntOrNull() ?: 10
            val _textSize = call.parameters["textSize"]?.toIntOrNull() ?: 0
            val found = mgr.getReviews(start = _start, count = _count, textSize = _textSize, status = "approved")
            call.respondApi(
                ApiResponse.Success(
                data = Pagination(
                    items = found.second.map { it.toPublicDTO() },
                    total = found.first,
                    page = _start / _count + 1,
                    pageSize = _count
                )
            ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/publicReviews/{id}") {
        runCatching {
            val id = call.parameters["id"]?.toIntOrNull()
            if(id == null) throw ValidationError("Не указан идентификатор отзыва")
            val found = mgr.getReview(id)
            if(found == null) throw ValidationError("Отзыв с id=$id не найден")
            call.respondApi(ApiResponse.Success(data = found.toPublicDTO()))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/user/reviews") {
        runCatching {
            log.withUser(call, "Запрос списка отзывов")
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val userMail = accessControl.getUserById(userId)?.mail ?: throw ValidationError("Пользователь не найден")
            val _textSize = call.request.queryParameters["textSize"]?.toIntOrNull() ?: 0
            val _start = call.parameters["start"]?.toIntOrNull() ?: 0
            val _count = call.parameters["count"]?.toIntOrNull() ?: 10
            // почта проверяется при сохранении и добавл пользов
            val found = mgr.getReviews(start = _start, count = _count, textSize = _textSize, mail = userMail)
            call.respondApi(
                ApiResponse.Success(
                    data = Pagination(
                        items = found.second.map { it.toUserDTO() },
                        total = found.first,
                        page = _start / _count + 1,
                        pageSize = _count
                    )
                ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/user/reviews/{id}") {
        runCatching {
            log.withUser(call, "Загрузка отзыва")
            val orderId = call.parameters["id"]?.toIntOrNull() ?: throw ValidationError("Не указан id отзыва")
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val userMail = accessControl.getUserById(userId)?.mail ?: throw ValidationError("Пользователь не найден")

            val found = mgr.getUserReview(id = orderId, mail = userMail)
            if(found == null) throw ValidationError("Отзыв не найден")
            call.respondApi(ApiResponse.Success(
                data = found.toUserDTO()
            ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    put("/user/reviews") {
        runCatching {
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val body = call.receive<Map<String, JsonElement>>().trimmed()
            val text = body["text"]?.jsonPrimitive?.content ?: throw ValidationError("Поле text не задано")
            if(text.isEmpty()) throw ValidationError("Поле text не задано")
            if(text.length > 2048) throw ValidationError("Поле text слишком длинное. Максимум 2048 символов")
            val rating = body["rating"]?.jsonPrimitive?.content?.toIntOrNull() ?: throw ValidationError("Поле rating не задано")
            if(rating !in 1..5) throw ValidationError("Поле rating может принимать значения от 1 до 5")

            //не больше 3х новых отзывов в сутки
            val user = accessControl.getUserById(userId) ?: throw ValidationError("Пользователь не найден")
            if(mgr.getReviews(dateStart = LocalDateTime.now().minusDays(1), mail = user.mail).second.size >= 3) {
                throw ValidationError("Нельзя оставлять больше 3х отзывов в сутки")
            }

            checkStopWords(text)
            val review = mgr.addReview(mail = user.mail, caption = user.name, text = text, rating = rating)
            val answer = jsonMap(
                "id" to review.id,
                "date" to review.date,
                "caption" to review.caption,
                "text" to review.text,
                "rating" to review.rating
            )

            call.respondApi(
                response = ApiResponse.Success(data = answer, message = "Отзыв добавлен",),
                statusCode = HttpStatusCode.Created)
        }
        .getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/user/reviews") {
        runCatching {
            call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val body = call.receive<Map<String, JsonElement>>().trimmed()
            val reviewId = body["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: throw ValidationError("Поле id не задано")
            val text = body["text"]?.jsonPrimitive?.content ?: throw ValidationError("Поле text не задано")
            if(text.isEmpty()) throw ValidationError("Поле text не задано")
            if(text.length > 2048) throw ValidationError("Поле text слишком длинное. Максимум 2048 символов")
            val rating = body["rating"]?.jsonPrimitive?.content?.toIntOrNull()
            rating?.let {
                if(it !in 1..5) throw ValidationError("Поле rating может принимать значения от 1 до 5")
            }

            checkStopWords(text)
            val review = mgr.editReview(id = reviewId, text = text, rating = rating)
            val answer = jsonMap(
                "id" to review.id,
                "date" to review.date,
                "caption" to review.caption,
                "text" to review.text,
                "rating" to review.rating
            )
            call.respondApi(
                response = ApiResponse.Success(data = answer, message = "Отзыв обновлен",),
            )
        }
        .getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    delete("/user/reviews") {
        runCatching {
            val userId = call.getUserId() ?: throw ValidationError("Авторизуйтесь на сайте")
            val userMail = accessControl.getUserById(userId)?.mail ?: throw ValidationError("Пользователь не найден")
            val ids = checkIds(call, "отзывы")
            ids.forEach {
                mgr.editReview(id = it, status = "deleted", mail = userMail)
            }
            val msg = "Отзывы удалены: ${ids.joinToString(", ")}"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }
        .getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/reviews") {
        runCatching {
            val _start = call.parameters["start"]?.toIntOrNull() ?: 0
            val _count = call.parameters["count"]?.toIntOrNull() ?: 10
            val _dateStart = call.parameters["dateStart"]?.let {
                LocalDateTime.parse(it)
            }
            val _dateEnd = call.parameters["dateEnd"]?.let {
                LocalDateTime.parse(it)
            }
            val _userName = call.parameters["userName"]
            val _userMail = call.parameters["userMail"]
            val _status = call.parameters["status"]
            val _text = call.parameters["text"]
            val _textSize = call.parameters["textSize"]?.toIntOrNull() ?: 0
            val _rating = call.parameters["rating"]?.toIntOrNull()
            _status?.let {
                if(it !in listOf("moderation", "deleted", "approved", "banned")) throw ValidationError("Недопустимый параметр status")
            }
            val found = mgr.getReviews(start = _start, count = _count, textSize = _textSize, dateStart = _dateStart,
                dateEnd = _dateEnd, status = _status, caption = _userName, mail = _userMail, text = _text, rating = _rating)
            call.respondApi(
                ApiResponse.Success(
                    data = Pagination(
                        items = found.second.map { it.toAdminDTO() },
                        total = found.first,
                        page = _start / _count + 1,
                        pageSize = _count
                    )
                ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/reviews/{id}") {
        runCatching {
            log.withUser(call, "Загрузка отзыва")
            val orderId = call.parameters["id"]?.toIntOrNull() ?: throw ValidationError("Не указан id отзыва")

            val found = mgr.getReview(id = orderId)
            if(found == null) throw ValidationError("Отзыв не найден")
            call.respondApi(ApiResponse.Success(data = found.toAdminDTO()))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/reviews") {
        runCatching {
            val body = call.receive<Map<String, JsonElement>>().trimmed()
            val reviewId = body["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: throw ValidationError("Поле id не задано")
            val text = body["text"]?.jsonPrimitive?.content
            val status = body["status"]?.jsonPrimitive?.content
            val rating = body["rating"]?.jsonPrimitive?.content?.toIntOrNull()
            rating?.let {
                if(it !in 1..5) throw ValidationError("Поле rating может принимать значения от 1 до 5")
            }
            status?.let {
                if(it !in listOf("moderation", "deleted", "approved", "banned")) throw ValidationError("Недопустимый параметр status")
            }
            text?.let {
                if(it.isEmpty()) throw ValidationError("Поле text не задано")
                if(it.length > 2048) throw ValidationError("Поле text слишком длинное. Максимум 2048 символов")
            }
            mgr.editReview(id = reviewId, text = text, status = status, rating = rating)
            call.respondApi<Unit>(ApiResponse.Success(message = "Отзыв обновлен"))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    delete("/reviews") {
        runCatching {
            val reviewId = call.parameters["id"]?.toIntOrNull() ?: throw ValidationError("Поле id не задано")
            mgr.editReview(id = reviewId, status = "deleted")
            call.respondApi<Unit>(ApiResponse.Success(message = "Отзыв удален"))
        }
        .getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/stopwords") {
        runCatching {
            val result = mgr.getStopWords()
            call.respondApi(ApiResponse.Success(
                data = result
            ))
        }
        .getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    post("/stopwords") {
        runCatching {
            val newWords = call.receive<List<String>>()
                .map { it.lowercase() }
                .toSet()
            if(newWords.any { it.length > 255 }) {
                throw ValidationError("Стоп-фразы слишком длинные. Максимум - 255 символов на фразу, каждая с новой строки")
            }
            mgr.saveStopWords(newWords)
            call.respondApi<Unit>(ApiResponse.Success(message = "Стоп-слова обновлены"))
        }
        .getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}