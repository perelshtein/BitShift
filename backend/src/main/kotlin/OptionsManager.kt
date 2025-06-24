package com.github.perelshtein

import com.github.perelshtein.AccessControl.Users.bindTo
import com.github.perelshtein.database.DatabaseAccess
import com.github.perelshtein.database.EncryptedStorage
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import org.ktorm.database.Database
import org.ktorm.entity.Entity
import org.ktorm.entity.add
import org.ktorm.entity.first
import org.ktorm.entity.firstOrNull
import org.ktorm.entity.sequenceOf
import org.ktorm.schema.Table
import org.ktorm.schema.boolean
import org.ktorm.schema.datetime
import org.ktorm.schema.double
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.TimeZone
import kotlin.getValue

// все основные настройки храним в базе
class OptionsManager: KoinComponent {
    private val dbAccess: DatabaseAccess by inject()
    private val optFromFile: Options by inject()
    private val storage: EncryptedStorage by inject()
    private val db = dbAccess.db
    private val validationPending = mutableMapOf<String, String>()

    val Database.options get() = this.sequenceOf(OptionsDb)
    val log = LoggerFactory.getLogger("OptionsManager")

    object OptionsDb : Table<OptionInDb>("options") {
        val id = int("id").primaryKey().bindTo { it.id }
        val sessionTimeoutMinutes = int("session_timeout_minutes").bindTo { it.sessionTimeoutMinutes }
        val maxRequestsSerious = int("max_requests_serious").bindTo { it.maxRequestsSerious }
        val maxRequests = int("max_requests").bindTo { it.maxRequests }
        val isRandomCookie = boolean("is_random_cookie").bindTo { it.isRandomCookie }
        var randomCookieInterval = int("random_cookie_interval").bindTo { it.randomCookieInterval }
        var randomCookieAttempts = int("random_cookie_attempts").bindTo { it.randomCookieAttempts }
        var randomCookieName = varchar("random_cookie_name").bindTo { it.randomCookieName }
        var randomCookieUpdated = datetime("random_cookie_updated").bindTo { it.randomCookieUpdated }
        var smtpServer = varchar("smtp_server").bindTo { it.smtpServer }
        var smtpPort = int("smtp_port").bindTo { it.smtpPort }
        var smtpLogin = varchar("smtp_login").bindTo { it.smtpLogin }
        var smtpPassword = varchar("smtp_password").bindTo { it.smtpPassword }
        var isExportCourses = boolean("is_export_courses").bindTo { it.isExportCourses }
        var isMaintenance = boolean("is_maintenance").bindTo { it.isMaintenance }
        var telegramBotToken = varchar("telegram_bot_token").bindTo { it.telegramBotToken }
        val telegramBotName = varchar("telegram_bot_name").bindTo { it.telegramBotName }
        val telegramGroupId = long("telegram_group_id").bindTo { it.telegramGroupId }
        val adminEmail = varchar("admin_email").bindTo { it.adminEmail }
        val cashbackPercent = double("cashback_percent").bindTo { it.cashbackPercent }
        val cashbackType = varchar("cashback_type").bindTo { it.cashbackType }
        val referralPercent = double("referral_percent").bindTo { it.referralPercent }
        var referralType = varchar("referral_type").bindTo { it.referralType }
    }

    init {
        if(db.options.firstOrNull() == null) {
            db.options.add(
                OptionInDb {
                    sessionTimeoutMinutes = DEFAULT_SESSION_TIME
                }
            )
            dbAccess.updateDeleteOldSessionsEvent(DEFAULT_SESSION_TIME)
        }
    }

    //добавим сюда warn, если нужно перезагрузить приложение
    fun getOptions(): OptionDTO {
        val dto = db.options.first().toDTO()
        log.debug("getOptions(): system timezone: ${TimeZone.getDefault().getRawOffset() / (60 * 1000)} minutes")
        log.debug("getOptions(): server timezone: ${dto.serverTimezoneMinutes} minutes")
        dto.validation = validationPending
        return dto
    }

    fun getHost(): String = Options().host

    fun getPort(): Int = Options().port

    fun getSmtpPassword(): String {
        return storage.decrypt(db.options.first().smtpPassword)
    }

    fun getTelegramToken(): String {
        return if(db.options.first().telegramBotToken.isBlank()) "" else storage.decrypt(db.options.first().telegramBotToken)
    }

    fun setOptions(newOptions: OptionDTO) {
        val opt = db.options.first()
        val checkedSessionTimeout = newOptions.sessionTimeoutMinutes.coerceIn(1, 60*24*7);
        if(opt.sessionTimeoutMinutes != checkedSessionTimeout) {
            log.info("Новая длительность сессий: $checkedSessionTimeout минут")
            dbAccess.updateDeleteOldSessionsEvent(checkedSessionTimeout)
        }
        opt.sessionTimeoutMinutes = checkedSessionTimeout

        if(newOptions.maxRequestsSerious <= 0 || newOptions.randomCookieAttempts <= 0 || newOptions.maxRequests <= 0) {
            throw ValidationError("Недопустимые значения макс. количества запросов. Они должны быть > 0")
        }
        if(newOptions.maxRequests != opt.maxRequests) {
            log.warn("Новые значения maxRequests: ${opt.maxRequests} -> ${newOptions.maxRequests}. Требуется перезапуск приложения.")
            opt.maxRequests = newOptions.maxRequests
            validationPending.put("maxRequests", "Требуется перезапуск приложения.")
        }
        if(newOptions.maxRequestsSerious != opt.maxRequestsSerious) {
            log.warn("Новые значения maxRequestsSerious: ${opt.maxRequestsSerious} -> ${newOptions.maxRequestsSerious}. Требуется перезапуск приложения.")
            opt.maxRequestsSerious = newOptions.maxRequestsSerious
            validationPending.put("maxRequestsSerious", "Требуется перезапуск приложения.")
        }
        if(newOptions.randomCookieAttempts != opt.randomCookieAttempts) {
            log.info("Новые значения randomCookieAttempts: ${opt.randomCookieAttempts} -> ${newOptions.randomCookieAttempts}")
            opt.randomCookieAttempts = newOptions.randomCookieAttempts
        }

        if(newOptions.smtpServer.isBlank()) throw ValidationError("Адрес сервера для отправки почты не указан. " +
                "Новый пользов не сможет создать заявку")
        opt.smtpServer = newOptions.smtpServer.trim()

        if(newOptions.smtpPort <= 0 || newOptions.smtpPort > 65535) throw ValidationError("Недопустимый порт для " +
                "отправки почты: ${newOptions.smtpPort}")
        opt.smtpPort = newOptions.smtpPort

        if(newOptions.smtpLogin.isBlank()) throw ValidationError("Логин для отправки почты не указан.")
        opt.smtpLogin = newOptions.smtpLogin.trim()

        if(newOptions.smtpPassword.isBlank()) throw ValidationError("Пароль для отправки почты не указан.")
        if(newOptions.smtpPassword != "******") opt.smtpPassword = storage.encrypt(newOptions.smtpPassword.trim())

        if(newOptions.telegramBotToken != "******") {
            if(newOptions.telegramBotToken.isNotBlank()) opt.telegramBotToken = storage.encrypt(newOptions.telegramBotToken.trim())
            else opt.telegramBotToken = ""
            log.warn("Новые значения telegramBotToken. Требуется перезапуск приложения.")
            validationPending.put("telegramBotToken", "Требуется перезапуск приложения.")
        }

        if(newOptions.telegramBotName != opt.telegramBotName) {
            opt.telegramBotName = newOptions.telegramBotName
            log.warn("Новые значения telegramBotName. Требуется перезапуск приложения.")
            validationPending.put("telegramBotName", "Требуется перезапуск приложения.")
        }

        opt.telegramGroupId = newOptions.telegramGroupId.toLongOrNull()
        opt.isExportCourses = newOptions.isExportCourses
        opt.isMaintenance = newOptions.isMaintenance
        opt.adminEmail = newOptions.adminEmail
        opt.cashbackPercent = newOptions.cashbackPercent
        opt.cashbackType = newOptions.cashbackType.value
        opt.referralPercent = newOptions.referralPercent
        opt.referralType = newOptions.referralType.value

        opt.flushChanges()

        if(newOptions.serverTimezoneMinutes !in -12*60..14*60) {
            throw ValidationError("Недопустимый часовой пояс: ${newOptions.serverTimezoneMinutes} минут. Допускается от -12 до +14 часов.")
        }
        val checkedServerTimezone = newOptions.serverTimezoneMinutes.coerceIn(-12*60, 14*60);
        if(TimeZone.getDefault().getRawOffset() / (60 * 1000) != checkedServerTimezone) {
            val timezoneId = getTimeZoneIDFromOffset(checkedServerTimezone)
            val msg = "Выбран другой часовой пояс: ${timezoneId}. Требуется перезапуск приложения."
            log.warn(msg)
            validationPending.put("serverTimezoneMinutes", msg)
        }

        if(optFromFile.timezoneOffsetMinutes != checkedServerTimezone) {
            optFromFile.timezoneOffsetMinutes = checkedServerTimezone
            optFromFile.flush()
        }
    }

    fun getRandomCookieUpdated(): LocalDateTime? {
        return db.options.first().randomCookieUpdated
    }

    fun setRandomCookieUpdated(updated: LocalDateTime) {
        val opt = db.options.first()
        opt.randomCookieUpdated = updated
        opt.flushChanges()
    }

    fun setCookieName(name: String) {
        val opt = db.options.first()
        opt.randomCookieName = name
        opt.flushChanges()
    }
}

interface OptionInDb: Entity<OptionInDb> {
    companion object : Entity.Factory<OptionInDb>()
    val id: Int
    var sessionTimeoutMinutes: Int
    var maxRequestsSerious: Int
    var maxRequests: Int
    var isRandomCookie: Boolean
    var randomCookieInterval: Int
    var randomCookieAttempts: Int
    var randomCookieName: String
    var randomCookieUpdated: LocalDateTime?
    var smtpServer: String
    var smtpPort: Int
    var smtpLogin: String
    var smtpPassword: String
    var isExportCourses: Boolean
    var isMaintenance: Boolean
    var telegramBotToken: String
    var telegramBotName: String
    var telegramGroupId: Long?
    var adminEmail: String
    var cashbackPercent: Double
    var cashbackType: String
    var referralPercent: Double
    var referralType: String
}

fun OptionInDb.toDTO(): OptionDTO {
    val optFromFile: Options by inject(Options::class.java)
    return OptionDTO(
        sessionTimeoutMinutes = this.sessionTimeoutMinutes,
        serverTimezoneMinutes = optFromFile.timezoneOffsetMinutes,
        maxRequests = this.maxRequests,
        maxRequestsSerious = this.maxRequestsSerious,
        isRandomCookie = this.isRandomCookie,
        randomCookieName = this.randomCookieName,
        randomCookieInterval = this.randomCookieInterval,
        randomCookieAttempts = this.randomCookieAttempts,
        smtpServer = this.smtpServer,
        smtpPort = this.smtpPort,
        smtpLogin = this.smtpLogin,
        smtpPassword = if(this.smtpPassword.isBlank()) "" else "******",
        isExportCourses = this.isExportCourses,
        isMaintenance = this.isMaintenance,
        telegramBotToken = if(this.telegramBotToken.isBlank()) "" else "******",
        telegramBotName = this.telegramBotName,
        telegramGroupId = this.telegramGroupId?.toString() ?: "",
        adminEmail = this.adminEmail,
        cashbackPercent = this.cashbackPercent,
        cashbackType = BONUS_TYPE.fromValue(this.cashbackType),
        referralPercent = this.referralPercent,
        referralType = BONUS_TYPE.fromValue(this.referralType)
    )
}

//fun OptionDTO.toDB(): OptionInDb {
//    return OptionInDb {
//        sessionTimeoutMinutes = this@toDB.sessionTimeoutMinutes
//    }
//}

@Serializable
data class OptionDTO(
    val sessionTimeoutMinutes: Int,
    var serverTimezoneMinutes: Int,
    var validation: Map<String, String> = mapOf(),
    var maxRequests : Int,
    var maxRequestsSerious : Int,
    var isRandomCookie: Boolean,
    var randomCookieName: String,
    var randomCookieInterval: Int,
    var randomCookieAttempts: Int,
    var smtpServer: String,
    var smtpPort: Int,
    var smtpLogin: String,
    var smtpPassword: String,
    var isExportCourses: Boolean,
    var isMaintenance: Boolean,
    var telegramBotToken: String,
    var telegramBotName: String,
    var telegramGroupId: String,
    var adminEmail: String,
    var cashbackPercent: Double,
    var cashbackType: BONUS_TYPE,
    var referralPercent: Double,
    var referralType: BONUS_TYPE
)

const val DEFAULT_SESSION_TIME = 30 //в минутах