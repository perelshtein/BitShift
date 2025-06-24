package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.ValidationError
import com.github.perelshtein.exchanges.Autopay
import com.github.perelshtein.exchanges.Bybit
import com.github.perelshtein.exchanges.PayCurrency
import com.github.perelshtein.exchanges.PayInAPI
import com.github.perelshtein.exchanges.PayOutAPI
import com.github.perelshtein.respondApi
import com.github.perelshtein.withUser
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.reflect.typeInfo
import org.koin.core.component.KoinComponent
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import kotlin.getOrElse


fun Route.autoPay() {
    val log = LoggerFactory.getLogger("Route.autoPay")
    val autopay: Autopay by KoinJavaComponent.inject<Autopay>(Autopay::class.java)

    get("/payin") {
        runCatching {
            val name = call.parameters["exchange"] ?: throw ValidationError("Не указана биржа")
            val payin = autopay.getPayin(name)
            val answer = payin.getCurrenciesPayin()
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }

    get("/payout") {
        runCatching {
            val name = call.parameters["exchange"] ?: throw ValidationError("Не указана биржа")
            val payout = autopay.getPayout(name)
            val answer = payout.getCurrenciesPayout()
            call.respondApi(ApiResponse.Success(data = answer))
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}