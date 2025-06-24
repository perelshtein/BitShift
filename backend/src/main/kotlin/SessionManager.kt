package com.github.perelshtein

import com.github.perelshtein.database.DatabaseAccess
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.get
import org.ktorm.database.Database
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.entity.Entity
import org.ktorm.entity.add
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import org.ktorm.schema.Table
import org.ktorm.schema.datetime
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

// учет сессий пользователей
class SessionManager: KoinComponent {
    object Sessions : Table<Session>("sessions") {
        val token = varchar("token").primaryKey().bindTo { it.token }
        val userId = int("user_id").bindTo { it.userId }
        val lastActivity = datetime("last_activity").bindTo { it.lastActivity }
    }

    val Database.sessions get() = this.sequenceOf(Sessions)

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val log = LoggerFactory.getLogger("SessionManager")

    fun getUserIdByToken(token: String): Int? {
        val session = db.sessions.find { it.token eq token } ?: return null
        log.debug("UserId найден по токену!")
        return session.userId
    }

    fun createSession(userId: Int): String {
        val token = java.util.UUID.randomUUID().toString()
        db.sessions.add(
            Session {
                this.token = token
                this.userId = userId
                this.lastActivity = LocalDateTime.now()
            }
        )

        log.debug("Создана новая сессия для ${getUserDesc(userId)}")
        return token
    }

    fun getSessionByUserId(userId: Int): Session? {
        return db.sessions.find { it.userId eq userId }
    }

    fun isSessionExists(token: String): Boolean {
        return db.sessions.find { it.token eq token } != null
    }

    fun validateSession(token: String): Boolean {
        val optionsManager: OptionsManager by inject()
        val sessionTimeoutMinutes = optionsManager.getOptions().sessionTimeoutMinutes
        val session = db.sessions.find { it.token eq token } ?: return false
        val now = LocalDateTime.now()
        val userId = getUserIdByToken(token)
        if (now.isAfter(session.lastActivity.plus(sessionTimeoutMinutes.toLong(), java.time.temporal.ChronoUnit.MINUTES))) {
            log.debug("lastActivity: ${session.lastActivity}, now: $now, sessionTimeoutMinutes: $sessionTimeoutMinutes")
            if (userId != null) log.debug("Сессия устарела. Удаляем ее для ${getUserDesc(userId)}")
            delete(token) // Сессия устарела, удаляем
            return false
        }
        // Обновить время последней активности сессии
        session.lastActivity = now
        session.flushChanges()
        if (userId != null) log.debug("Обновляем сессию для ${getUserDesc(userId)}")

        return true
    }

    fun delete(token: String) {
        val userId = getUserIdByToken(token)
        if (userId != null) log.debug("Удаляем сессию для ${getUserDesc(userId)}")
        db.delete(Sessions) { it.token eq token }
    }

    private fun getUserDesc(userId: Int): String {
        val access: AccessControl = get(AccessControl::class.java)
        access.getUserById(userId)?.name?.let {
            return "${it} (id = ${userId})"
        }
        return "userId = ${userId}"
    }
}

interface Session: Entity<Session> {
    companion object : Entity.Factory<Session>()
    var token: String
    var userId: Int
    var lastActivity: LocalDateTime
}