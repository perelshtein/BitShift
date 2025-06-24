package com.github.perelshtein.database

import org.koin.core.component.KoinComponent
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

class VerifyLinkManager : KoinComponent {
    private val links = ConcurrentHashMap<String, VerifyLink>()
    private val log = LoggerFactory.getLogger("VerifyLinkManager")

    fun add(emailParam: String): String {
        val token = UUID.randomUUID().toString()
        links[token] = VerifyLink(email = emailParam)
        log.debug("Сгенерирован токен для регистрации $emailParam")
        return token
    }

    fun getEmailByToken(token: String): String? = links[token]?.email

    fun deleteToken(token: String) {
        links.remove(token)
    }

    fun cleanOldLinks() {
        val now = LocalDateTime.now()
        links.entries.removeIf { entry ->
            val expired = entry.value.deadline.isBefore(now)
            if (expired) log.info("Автоудаление устаревшей ссылки для регистрации ${entry.key} для ${entry.value.email}")
            expired
        }
    }
}

data class VerifyLink(
    val deadline: LocalDateTime = LocalDateTime.now().plusMinutes(5L),
    val email: String
)