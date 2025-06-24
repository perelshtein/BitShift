package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.Options
import com.github.perelshtein.OptionsManager
import com.github.perelshtein.Pagination
import com.github.perelshtein.ValidationError
import com.github.perelshtein.checkIds
import com.github.perelshtein.database.Course
import com.github.perelshtein.database.FormulaDTO
import com.github.perelshtein.database.FormulaManager
import com.github.perelshtein.database.toDB
import com.github.perelshtein.database.toDTO
import com.github.perelshtein.exchanges.CoinMarketCap
import com.github.perelshtein.exchanges.CrossCourseResult
import com.github.perelshtein.exchanges.CrossCourses
import com.github.perelshtein.exchanges.FormulasGenerateAnswer
import com.github.perelshtein.respondApi
import com.github.perelshtein.withUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.util.Random


fun Route.formulas() {
    val log = LoggerFactory.getLogger("Route.formulas")
    val mgr: FormulaManager = KoinJavaComponent.get(FormulaManager::class.java)

    get("/formulas") {
        runCatching {
            val start = call.parameters["start"]?.toInt() ?: 0
            val count = call.parameters["count"]?.toInt() ?: 100
            val isActive = call.parameters["isActive"]?.toBoolean()
            val filter = call.parameters["filter"]
            val found = mgr.getFormulas(start, count, isActive, filter)
            call.respondApi(
                ApiResponse.Success(
                    data = Pagination(
                        items = found.second,
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

    get("/formula") {
        runCatching {
            val from = call.parameters["from"].orEmpty()
            val to = call.parameters["to"].orEmpty()
            mgr.getFormula(from, to)?.let {
                call.respondApi(ApiResponse.Success(data = it))
                return@get
            }
            call.respondApi<Unit>(ApiResponse.Warning(
                message = "Формула не найдена"
            ))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    suspend fun formulaHandler(call: ApplicationCall, action: String) {
        runCatching {
            val formula = call.receive<FormulaDTO>()
            mgr.upsertFormula(formula.toDB())
            val msg = "Формула ${formula.from} -> ${formula.to} ${action}"
            log.withUser(call, msg)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    put("/formula") {
        formulaHandler(call, "добавлена")
    }

    post("/formula") {
        formulaHandler(call, "обновлена")
    }

    get("/formulas/generate") {
        runCatching {
            val from = call.parameters["from"] ?: throw ValidationError("Валюта Отдаю не указана")
            val to = call.parameters["to"] ?: throw ValidationError("Валюта Получаю не указана")
            if (from == to) throw ValidationError("Коды Отдаю и Получаю должны отличаться")
            val calcMode = call.parameters["calcMode"] ?: "auto"
            when (calcMode) {
                "auto" -> {
                    val cross: CrossCourses = KoinJavaComponent.get(CrossCourses::class.java)
                    val courses = cross.findCrossCourses(from, to)
                    call.respondApi(ApiResponse.Success(data = courses))
                }

                "CoinMarketCap" -> {
                    val coinMarketCap: CoinMarketCap = KoinJavaComponent.get(CoinMarketCap::class.java)
                    val price = coinMarketCap.getReferencePrice(from, to)
                        ?: throw ValidationError("Ошибка загрузки курса с CoinMarketCap")
                    val answer = FormulasGenerateAnswer(
                        referentPrice = price,
                        referentSource = "CoinMarketCap",
                        list = listOf(
                            CrossCourseResult(
                                path = "$from -> $to",
                                price = price,
                                tag = "CoinMarketCap_${from}_${to}",
                                spread = 0.0
                            )
                        )
                    )
                    call.respondApi(ApiResponse.Success(data = answer))
                }

                "fromFile" -> {
                    val answer = loadCourseFromFile(from, to)
                    call.respondApi(ApiResponse.Success(data = answer))
                }

                else -> throw ValidationError("Неизвестная опция calcMode: $calcMode")
            }
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    delete("/formulas") {
        runCatching {
            val ids = checkIds(call, "формулы")
            val names = ids.map {
                mgr.getFormulaName(it)
            }.joinToString(", ")
            ids.forEach {
                mgr.deleteFormula(it)
            }
            val msg = "Удалены формулы: $names"
            log.withUser(call, names)
            call.respondApi<Unit>(ApiResponse.Success(message = msg))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}

fun loadCourseFromFile(from: String, to: String): FormulasGenerateAnswer {
    val filename = "kurs.json"
    if (!File(filename).exists()) throw ValidationError("Файл курсов ${filename} не существует")
    val text = File(filename).readText()
    if (text.trim().isEmpty()) {
        throw ValidationError("Файл курсов ${filename} пуст")
    }

    val courses = Json.decodeFromString<List<CourseFromFile>>(text)
    courses.firstOrNull { it.from == from && it.to == to && it.price > 0.0 }?.let {
        val answer = FormulasGenerateAnswer(
            referentPrice = it.price,
            referentSource = "fromFile",
            list = listOf(
                CrossCourseResult(
                    path = "$from -> $to",
                    price = it.price,
                    tag = "fromFile_${from}_${to}",
                    spread = 0.0
                )
            )
        )
        return answer
    }
    throw ValidationError("Задайте курс в файле ${filename}")
}

@Serializable
data class FormulasAnswer(
    val count: Int,
    val list: List<FormulaDTO>
)

@Serializable
data class CourseFromFile(
    val from: String,
    val to: String,
    val price: Double
)