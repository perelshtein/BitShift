package com.github.perelshtein.exchanges

import com.github.perelshtein.database.CourseManager
import com.github.perelshtein.database.Exchange
import com.github.perelshtein.database.ExchangeManager
import org.koin.core.component.KoinComponent
import org.slf4j.LoggerFactory
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.inject
import java.time.LocalDateTime

class Garantex: KoinComponent, ExchangeAPI {
    val mgr: ExchangeManager by inject()
    val client: HttpClient by inject()
    val GARANTEX = Garantex::class.simpleName!!
    val log = LoggerFactory.getLogger("Garantex")
    private var exchangeInfo: Exchange

    init {
        if(mgr.getExchangeByName(GARANTEX) == null) {
            mgr.addExchange(Exchange {
                name = GARANTEX
                updatePeriod = 1
                maxFailCount = 5
                isEnabled = true
                url = "https://garantex.org"
                lastUpdate = LocalDateTime.now().minusMinutes(2L)
            })
        }
        exchangeInfo = mgr.getExchangeByName(GARANTEX)!!
    }

    override suspend fun fetchCourses(): List<Course> {
        runCatching {
            log.debug("Загрузка курсов...")
            exchangeInfo = mgr.getExchangeByName(GARANTEX)!!
            val response = client.get("${exchangeInfo.url}/rates")
            if (response.status != HttpStatusCode.OK) throw Exception("Код ошибки HTTP: ${response.status}")
            val rates = response.body<Map<String, Rate>>().filter { it.value.buy != null && it.value.sell != null }

            return rates.map {
                // для совместимости форматируем в верхний регистр
                Course(from = it.key.uppercase(), to = "", buy = it.value.buy ?: 0.0, sell = it.value.sell ?: 0.0)
            }
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось получить курсы.\n${msg}")
            throw exception
        }
    }

    override suspend fun fetchCodes(): Set<String> {
        runCatching {
            log.debug("Загрузка торговых пар...")
            exchangeInfo = mgr.getExchangeByName(GARANTEX)!!
            val response = client.get("${exchangeInfo.url}/api/v2/markets")
            if (response.status != HttpStatusCode.OK) throw Exception("Код ошибки HTTP: ${response.status}")

            return response.body<List<MarketPair>>()
                .flatMap { listOf(it.ask_unit, it.bid_unit) }
                .map { it.uppercase() }
                .toSet()

        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось получить список пар.\n${msg}")
            throw exception
        }
    }
}

@Serializable
data class Rate(
    val buy: Double?,
    val sell: Double?
)

@Serializable
data class MarketPair(
    val id: String,
    val ask_unit: String,
    val bid_unit: String
)