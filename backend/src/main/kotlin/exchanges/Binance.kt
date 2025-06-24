package com.github.perelshtein.exchanges

import com.github.perelshtein.database.Exchange
import com.github.perelshtein.database.ExchangeManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.getValue

class Binance: KoinComponent, ExchangeAPI {
    val mgr: ExchangeManager by inject()
    val client: HttpClient by inject()
    val BINANCE = Binance::class.simpleName!!
    val log = LoggerFactory.getLogger("Binance")
    private var exchangeInfo: Exchange

    init {
        if(mgr.getExchangeByName(BINANCE) == null) {
            mgr.addExchange(Exchange {
                name = BINANCE
                updatePeriod = 1
                maxFailCount = 5
                isEnabled = true
                url = "https://api.binance.com"
                lastUpdate = LocalDateTime.now().minusMinutes(2L) // чтобы при добавлении биржи загрузились курсы
            })
        }
        exchangeInfo = mgr.getExchangeByName(BINANCE)!!
    }

    override suspend fun fetchCodes(): Set<String> {
        runCatching {
            log.debug("Загрузка торговых пар...")
            exchangeInfo = mgr.getExchangeByName(BINANCE)!!
            val response = client.get("${exchangeInfo.url}/api/v3/exchangeInfo") {
                url {
                    parameters.append("permissions", "SPOT")
                    parameters.append("showPermissionSets", "false")
                    parameters.append("symbolStatus", "TRADING")
                }
            }
            if (response.status != HttpStatusCode.OK) throw Exception("Код ошибки HTTP: ${response.status}")

            return response.body<BinanceInfo>()
                .symbols
                .flatMap { listOf(it.baseAsset, it.quoteAsset) }
                .toSet()

        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось получить список пар.\n${msg}")
            throw exception
        }
    }

    override suspend fun fetchCourses(): List<Course> {
        runCatching {
            log.debug("Загрузка курсов...")
            exchangeInfo = mgr.getExchangeByName(BINANCE)!!
            val response = client.get("${exchangeInfo.url}/api/v3/ticker/bookTicker")
            if (response.status != HttpStatusCode.OK) throw Exception("Код ошибки HTTP: ${response.status}")

            return response.body<List<BinancePrice>>()
                .map {
                    Course(from = it.symbol, to = "", buy = it.bidPrice, sell = it.askPrice)
                }

        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось получить курсы.\n${msg}")
            throw exception
        }
    }
}

@Serializable
data class BinanceInfo(
    val symbols: List<Symbol>
) {
    @Serializable
    data class Symbol(
        val baseAsset: String,
        val quoteAsset: String
    )
}

@Serializable
data class BinancePrice(
    val symbol: String,
    val bidPrice: Double,
    val askPrice: Double
)