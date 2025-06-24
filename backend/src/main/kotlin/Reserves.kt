package com.github.perelshtein

import com.github.perelshtein.database.CurrencyManager
import com.github.perelshtein.exchanges.BalanceAPI
import com.github.perelshtein.exchanges.Bybit
import com.github.perelshtein.exchanges.Mexc
import com.github.perelshtein.exchanges.PayInAPI
import com.github.perelshtein.routes.ReserveResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import java.io.File
import java.time.LocalDateTime

class ReserveFormatter: KoinComponent {
    fun loadFromFile(currency: String): ReserveResponse {
        val filename = "reserve.json"
        if (!File(filename).exists()) throw ValidationError("Файл резерва ${filename} не существует")
        val text = File(filename).readText()
        if (text.trim().isEmpty()) {
            throw ValidationError("Файл резерва ${filename} пуст")
        }
        val fromFile = Json.decodeFromString<List<Reserve>>(text)
            .firstOrNull { it.baseCurrency == currency }
            ?: throw ValidationError("Резерв для валюты ${currency} не найден в файле ${filename}")

        return ReserveResponse(
            "fromFile",
            fromFile.reserveCurrency,
            fromFile.value
        )
    }

    suspend fun calcReserve(reserve: com.github.perelshtein.database.Reserve): ReserveResponse {
        when (reserve.reserveType) {
            // даже в режиме "Из файла" берем значение из базы - нам важен остаток,
            // а не начальное значение
            "fromFile" -> {
                return ReserveResponse(
                    "fromFile",
                    reserve.reserveCurrency,
                    reserve.value
                )
            }

            "manual" -> {
                return ReserveResponse(
                    "manual",
                    reserve.reserveCurrency,
                    reserve.value
                )
            }

            "fromExchange" -> {
                if(reserve.lastUpdated.plusSeconds(5).isBefore(LocalDateTime.now())) {
                    //обновим данные в базе
                    val mgr: CurrencyManager by inject()
                    val exchange: BalanceAPI = when(reserve.exchangeName.uppercase()) {
                        "BYBIT" -> KoinJavaComponent.get(Bybit::class.java)
                        "MEXC" -> KoinJavaComponent.get(Mexc::class.java)
                        else -> throw ValidationError("Не поддерживается биржа: ${reserve.exchangeName}")
                    }
                    reserve.value = exchange.fetchBalance()
                    reserve.lastUpdated = LocalDateTime.now()
                    mgr.upsertReserve(reserve)
                }

                return ReserveResponse(
                    "fromExchange",
                    reserve.reserveCurrency,
                    reserve.value,
                    reserve.exchangeName
                )
            }

            "off" -> {
                return ReserveResponse("off")
            }

            else -> throw ValidationError("Неизвестный тип резерва для ${reserve.currency}: ${reserve.reserveType}")
        }
    }

    @Serializable
    data class Reserve(
        val baseCurrency: String,
        val reserveCurrency: String,
        val value: Double
    )
}