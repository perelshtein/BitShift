package com.github.perelshtein

import com.github.perelshtein.database.CurrencyManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TempOrderStorage: KoinComponent {
    private val orders = ConcurrentHashMap<String, TempOrder>()
    private val currencies = ConcurrentHashMap<Int, String>()
    private val log = LoggerFactory.getLogger("TempOrderStorage")

    init {
        updateCurrencies()
    }

    fun add(order: TempOrder): String {
        val token = UUID.randomUUID().toString()
        orders[token] = order
        log.info("Добавлена временная заявка $token")
        return token
    }

    fun get(orderId: String): TempOrder? = orders[orderId]

    fun delete(orderId: String) = orders.remove(orderId)

    fun refresh() {
        updateCurrencies()
        cleanOldOrders()
    }

    private fun updateCurrencies() {
        val mgr by inject<CurrencyManager>()
        currencies.putAll(mgr.getCurrencyNamesMap())
    }

    private fun cleanOldOrders() {
        val now = LocalDateTime.now()
        orders.entries.removeIf { entry ->
            val expired = entry.value.deadline.isBefore(now)
            if(expired) {
                val order = entry.value
                val fromCurrency = currencies[order.currencyFromId] ?: "Unknown(id = ${order.currencyFromId})"
                val toCurrency = currencies[order.currencyToId] ?: "Unknown(id = ${order.currencyToId})"
                log.info("Автоудаление устаревшей временной заявки ${entry.key}: " +
                    "${order.amountFrom} ${fromCurrency} -> ${order.amountTo} ${toCurrency}")
            }
            expired
        }
    }
}

data class TempOrder(
    val currencyFromId: Int,
    val currencyToId: Int,
    val wallet: String,
    val amountFrom: Double,
    val amountTo: Double,
    val fieldsGive: Map<String, String>? = null,
    val fieldsGet: Map<String, String>? = null,
    val deadline: LocalDateTime = LocalDateTime.now().plusMinutes(5L),
    val refId: Int? = null
)