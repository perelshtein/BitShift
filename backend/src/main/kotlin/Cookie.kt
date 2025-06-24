package com.github.perelshtein

import com.github.perelshtein.database.CookieBan
import com.github.perelshtein.database.CookieBanManager
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.util.date.GMTDate
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.min

class CookieGen {
    val optionsManager: OptionsManager = get(OptionsManager::class.java)
    val optFromFile: Options = get(Options::class.java)
    val log = LoggerFactory.getLogger("CookieGen")

    fun generate(call: ApplicationCall, token: String) {
        log.withUser(call, "Генерируем куки", LOG_TYPE.DEBUG)
        val sessionPeriod = optionsManager.getOptions().sessionTimeoutMinutes
        call.response.cookies.append(
            Cookie(
                name = COOKIE_AUTH_TOKEN,
                value = token,
                httpOnly = true,
                secure = COOKIE_PRODUCTION, // Set true in production
                path = "/",
                extensions = mapOf("SameSite" to "strict"),
                maxAge = sessionPeriod * 60, // в секундах
                domain = if(COOKIE_PRODUCTION) optFromFile.host else null
            )
        )
    }

    fun delete(call: ApplicationCall) {
        log.withUser(call, "Удаляем куки", LOG_TYPE.DEBUG)
        call.response.cookies.append(
            Cookie(
                name = COOKIE_AUTH_TOKEN,
                value = "game over",
                httpOnly = true,
                secure = COOKIE_PRODUCTION,
                path="/",
                maxAge = 0,
                domain = if(COOKIE_PRODUCTION) optFromFile.host else null,
                extensions = mapOf("SameSite" to "strict"),
                expires = GMTDate()
            )
        )
    }
}

val CookieProtection = createApplicationPlugin(name = "CookieProtection") {
    onCall { call ->
        val optFromFile: Options = get(Options::class.java)
        val ip = call.getRealIP()
        val authToken = call.request.cookies[COOKIE_AUTH_TOKEN]
        val sessionManager: SessionManager = get(SessionManager::class.java)
        val optionsManager: OptionsManager = get(OptionsManager::class.java)
        val banManager: CookieBanManager = get(CookieBanManager::class.java)
        val opt = optionsManager.getOptions()
        val now = LocalDateTime.now()
        val log = LoggerFactory.getLogger("CookieProtection")

        if (authToken == null || !sessionManager.validateSession(authToken)) {
            val cookieName = opt.randomCookieName
            // Сколько осталось токенов. Если имя куки не совпадает, получим null.
            val cookieCnt = call.request.cookies[cookieName]?.let {
                it.split("tokensRemaining:").getOrNull(1)?.toDoubleOrNull()
            }
            val found = banManager.getVisit(ip)
            if (found != null && found.banExpiry != null && now.isBefore(found.banExpiry)) {
                val msg = "Ваш ip-адрес забанен до ${found.banExpiry}"
                throw AttackError(msg)
                log.withUser(call, msg, LOG_TYPE.WARN)
                return@onCall
            }

            // Token bucket
            val lastRefill = found?.lastRefill ?: now
            val dbTokens = found?.tokens?.toDouble() ?: opt.randomCookieAttempts.toDouble()
            val refillRate = opt.randomCookieAttempts / (opt.randomCookieInterval * 60.0) // Fixed max rate
            val elapsedSeconds = ChronoUnit.SECONDS.between(lastRefill, now).toDouble()
            val newTokens = min(opt.randomCookieAttempts.toDouble(), dbTokens + (elapsedSeconds * refillRate))

            // Проверим cookie, не подделан ли
            val tokens = if (cookieCnt != null && cookieCnt <= dbTokens + 1.0 && cookieCnt >= dbTokens - 1.0) {
                // Значение совпадает с базой (+-1) - пропускаем
                newTokens
                // Имя куки совпадает, счетчик совпадает. Даем зеленый свет.
                return@onCall
            } else {
                //Нет куки или подделка → сбрасываем в макс. кол-во, если первый визит, иначе берем счетчик из DB
                if (found == null) opt.randomCookieAttempts.toDouble() else newTokens
            }

            if (tokens >= 1.0) {
                banManager.setVisit(CookieBan {
                    this.ipAddress = ip
                    this.tokens = tokens - 1.0 // Keep Double
                    this.lastRefill = now
                    this.banExpiry = null
                })
                call.response.cookies.append(
                    Cookie(
                        name = opt.randomCookieName,
                        value = "tokensRemaining:${(tokens - 1.0).toInt()}",
                        httpOnly = true,
                        secure = COOKIE_PRODUCTION,
                        path = "/",
                        maxAge = opt.randomCookieInterval * 60,
                        domain = if(COOKIE_PRODUCTION) optFromFile.host else null,
                        extensions = mapOf("SameSite" to "strict")
                    )
                )
            } else {
                val banSeconds = (1.0 / refillRate).toLong() // Время в секундах для генерации одного токена
                val expire = now.plusSeconds(banSeconds)
                banManager.setVisit(CookieBan {
                    this.ipAddress = ip
                    this.tokens = tokens
                    this.lastRefill = now
                    this.banExpiry = expire
                })
                val msg = "Ваш ip-адрес забанен до ${expire}"
                throw AttackError(msg)
                log.withUser(call, msg, LOG_TYPE.WARN)
                return@onCall
            }
        }
    }
}