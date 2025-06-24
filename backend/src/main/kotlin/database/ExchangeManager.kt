package com.github.perelshtein.database

import com.github.perelshtein.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.ktorm.database.Database
import org.ktorm.dsl.eq
import org.ktorm.entity.Entity
import org.ktorm.entity.add
import org.ktorm.entity.filter
import org.ktorm.entity.find
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.update
import org.ktorm.schema.Table
import org.ktorm.schema.boolean
import org.ktorm.schema.datetime
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.getValue

class ExchangeManager: KoinComponent {
    object Exchanges : Table<Exchange>("exchanges") {
        val id = int("id").primaryKey().bindTo { it.id }
        val name = varchar("name").bindTo { it.name }
        val updatePeriod = int("update_period").bindTo { it.updatePeriod }
        val maxFailCount = int("max_fail_count").bindTo { it.maxFailCount }
        val lastUpdate = datetime("last_update").bindTo { it.lastUpdate }
        val isEnabled = boolean("is_enabled").bindTo { it.isEnabled }
        val url = varchar("url").bindTo { it.url }
        val blacklist = varchar("blacklist").bindTo { it.blacklist }
    }

    object ApiKeys: Table<ApiKey>("api_keys") {
        val id = int("id").primaryKey().bindTo { it.id }
        val exchangeId = int("exchange_id").bindTo { it.exchangeId }
        val apiKey = varchar("api_key").bindTo { it.apiKey }
        val secretKey = varchar("secret_key").bindTo { it.secretKey }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    private val log = LoggerFactory.getLogger("ExchangeManager")
    val Database.exchanges get() = this.sequenceOf(Exchanges)
    val Database.apiKeys get() = this.sequenceOf(ApiKeys)

    fun getExchanges(): List<Exchange> {
        return db.exchanges.map { it }
    }

    fun getExchangeByName(name: String): Exchange? {
        return db.exchanges.find { it.name eq name }
    }

    fun getExchangeById(id: Int): Exchange? {
        return db.exchanges.find { it.id eq id }
    }

    fun addExchange(exchange: Exchange) {
        db.exchanges.add(exchange)
        log.info("Добавлена биржа: \"${exchange.name}\"")
    }

    fun updateExchange(exchange: Exchange) {
        db.exchanges.update(exchange)
        if(!exchange.isEnabled) {
            val courseMgr: CourseManager by inject()
            courseMgr.clearPrice(exchange.id)
        }
    }

    fun getEnabledExchanges(): List<Int> = db.exchanges.filter { it.isEnabled }.map { it.id }

    fun getApiKeys(exchangeId: Int): ApiKey? {
        val storage: EncryptedStorage by inject()
        return db.apiKeys.find { it.exchangeId eq exchangeId }
            ?.apply {
                apiKey = storage.decrypt(apiKey)
                secretKey = storage.decrypt(secretKey)
            }
    }

    fun setApiKeys(exchangeId: Int, apiKey: ApiKey) {
        val storage: EncryptedStorage by inject()
        val old = db.apiKeys.find { it.exchangeId eq exchangeId }
        val newApiKey = if (apiKey.apiKey != "******") storage.encrypt(apiKey.apiKey) else null
        val newSecretKey = if (apiKey.secretKey != "******") storage.encrypt(apiKey.secretKey) else null
        if(newApiKey == null && newSecretKey == null) return

        if (old != null) {
            newApiKey?.let { old.apiKey = newApiKey }
            newSecretKey?.let { old.secretKey = newSecretKey }
            old.flushChanges()
            log.info("API-ключ и/или секретный ключ обновлены")
        } else {
            db.apiKeys.add(ApiKey {
                this.exchangeId = exchangeId
                newApiKey?.let { this.apiKey = newApiKey }
                newSecretKey?.let { this.secretKey = newSecretKey }
            })
            log.info("Добавлен новый API-ключ и/или секретный ключ")
        }
    }

}

interface Exchange: Entity<Exchange> {
    companion object : Entity.Factory<Exchange>()
    val id: Int
    var name: String
    var updatePeriod: Int //интервал между обновл курсов, в секундах. Чтобы сэкономить запросы к CoinMarketCap
    var maxFailCount: Int //сколько раз подряд допускается ошибка при получении курса
    var lastUpdate: LocalDateTime
    var isEnabled: Boolean
    var url: String
    var blacklist: String
}

fun Exchange.toDTO(): ExchangeRecordDTO {
    return ExchangeRecordDTO(
        name = this.name,
        updatePeriod = this.updatePeriod,
        maxFailCount = this.maxFailCount,
        lastUpdate = this.lastUpdate,
        isEnabled = this.isEnabled,
        url = this.url,
        blacklist = Json.decodeFromString(this.blacklist)
    )
}

@Serializable
data class ExchangeRecordDTO(
    val name: String = "",
    val updatePeriod: Int = 1,
    val maxFailCount: Int = 3,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastUpdate: LocalDateTime = LocalDateTime.now(),
    val isEnabled: Boolean = false,
    val url: String = "",
    val blacklist: List<String> = emptyList<String>(),

    //эти опции есть не у всех бирж
    val apiKey: String = "******", // ****** значит, что он не менялся. При сохранении проверим.
    val secretKey: String = "******", // ****** значит, что он не менялся. При сохранении проверим.
)

interface ApiKey: Entity<ApiKey> {
    companion object : Entity.Factory<ApiKey>()
    val id: Int
    var exchangeId: Int
    var apiKey: String
    var secretKey: String
}