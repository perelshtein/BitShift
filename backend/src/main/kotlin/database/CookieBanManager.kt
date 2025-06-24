package com.github.perelshtein.database

import com.github.perelshtein.OptionsManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.get
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.dsl.isNotNull
import org.ktorm.dsl.lt
import org.ktorm.entity.Entity
import org.ktorm.entity.add
import org.ktorm.entity.find
import org.ktorm.entity.removeIf
import org.ktorm.entity.sequenceOf
import org.ktorm.schema.Table
import org.ktorm.schema.datetime
import org.ktorm.schema.double
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.getValue

class CookieBanManager: KoinComponent {
    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    private var lastCleanTime: LocalDateTime? = null
    val Database.cookieBans get() = this.sequenceOf(CookieBans)
    val log = LoggerFactory.getLogger("CookieBanManager")
    val optionsManager: OptionsManager by inject()

    object CookieBans: Table<CookieBan>("cookie_bans") {
        val ipAddress = varchar("ip_address").primaryKey().bindTo { it.ipAddress }
        val tokens = double("tokens").bindTo { it.tokens }
        val lastRefill = datetime("last_refill").bindTo { it.lastRefill }
        val banExpiry = datetime("ban_expiry").bindTo { it.banExpiry }
    }

    fun getVisit(ipAddress: String): CookieBan? {
        return db.cookieBans.find { it.ipAddress eq ipAddress }
    }

    fun setVisit(cookieBan: CookieBan) {
        db.cookieBans.find { it.ipAddress eq cookieBan.ipAddress }?.let {
            it.tokens = cookieBan.tokens
            it.lastRefill = cookieBan.lastRefill
            it.banExpiry = cookieBan.banExpiry
            it.flushChanges()
            return
        }
        db.cookieBans.add(cookieBan)
    }

    fun checkOldBans() {
        val opt = optionsManager.getOptions()
        val now = LocalDateTime.now()
        val lastClean = lastCleanTime
        if(lastClean == null || lastClean.plusMinutes(opt.randomCookieInterval.toLong()).isBefore(now)) {
            log.info("Проверка таблицы банов..")
            val minTime = now.minusMinutes(opt.randomCookieInterval.toLong())
            db.cookieBans.removeIf {
                (it.lastRefill lt minTime) and (it.banExpiry.isNotNull()) and (it.banExpiry lt now) and (it.tokens lt 1.0)
            }
            lastCleanTime = now
            log.info("Таблица банов обновлена, старые записи удалены.")
        }
    }

    fun checkCookieName() {
        val opt = optionsManager.getOptions()
        // Если время пришло, сгенерируем новое имя для cookie
        val now = LocalDateTime.now()
        if(opt.isRandomCookie && (optionsManager.getRandomCookieUpdated() == null || optionsManager
                .getRandomCookieUpdated()!!.plusMinutes(opt.randomCookieInterval.toLong()).isBefore(now))
        ) {
            log.info("Генерация нового названия cookie..")
            val uuid = java.util.UUID.randomUUID().toString()
            optionsManager.setCookieName(uuid)
            optionsManager.setRandomCookieUpdated(now)
        }
    }
}

interface CookieBan: Entity<CookieBan> {
    companion object : Entity.Factory<CookieBan>()
    var ipAddress: String
    var tokens: Double
    var lastRefill: LocalDateTime
    var banExpiry: LocalDateTime?
}
