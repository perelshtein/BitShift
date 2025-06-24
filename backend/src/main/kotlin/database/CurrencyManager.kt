package com.github.perelshtein.database

import com.github.perelshtein.ValidationError
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.ktorm.database.Database
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.inList
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.ktorm.entity.Entity
import org.ktorm.entity.add
import org.ktorm.entity.any
import org.ktorm.entity.find
import org.ktorm.entity.isEmpty
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.update
import org.ktorm.schema.Table
import org.ktorm.schema.boolean
import org.ktorm.schema.datetime
import org.ktorm.schema.double
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.getValue

class CurrencyManager: KoinComponent {
    object Currencies : Table<CurrencyRecord>("currencies") {
        val id = int("id").primaryKey().bindTo { it.id }
        val name = varchar("name").bindTo { it.name }
        val code = varchar("code").bindTo { it.code }
        val xmlCode = varchar("xml_code").bindTo { it.xmlCode }
        val fidelity = int("fidelity").bindTo { it.fidelity }
        val acctValidator = varchar("acct_validator").bindTo { it.acctValidator }
        val acctChain = varchar("acct_chain").bindTo { it.acctChain }
        val isEnabled = boolean("is_enabled").bindTo { it.isEnabled }
        val payin = varchar("payin").bindTo { it.payin }
        val payinCode = varchar("payin_code").bindTo { it.payinCode }
        val payinChain = varchar("payin_chain").bindTo { it.payinChain }
        val payout = varchar("payout").bindTo { it.payout }
        val payoutCode = varchar("payout_code").bindTo { it.payoutCode }
        val payoutChain = varchar("payout_chain").bindTo { it.payoutChain }
    }

    object Reserves: Table<Reserve>("reserves") {
        val id = int("id").primaryKey().bindTo { it.id }
        val currency = varchar("currency").bindTo { it.currency }
        val reserveCurrency = varchar("reserve_currency").bindTo { it.reserveCurrency }
        val reserveType = varchar("reserve_type").bindTo { it.reserveType }
        val value = double("value").bindTo { it.value }
        val exchangeName = varchar("exchange_name").bindTo { it.exchangeName }
        val lastUpdated = datetime("last_updated").bindTo { it.lastUpdated }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val log = LoggerFactory.getLogger("CurrencyManager")
    val Database.currency get() = this.sequenceOf(Currencies)
    val Database.reserves get() = this.sequenceOf(Reserves)

    init {
        if(db.currency.isEmpty()) {
            addCurrency(CurrencyRecord {
                name = "USDT BEP-20"
                code = "USDT"
                xmlCode = "USDTBEP20"
                fidelity = 2
                acctValidator = "USDT"
                acctChain = "BEP20"
                isEnabled = true
                payin = "manual"
                payout = "manual"
            })
        }
    }

    fun addCurrency(currencyRecord: CurrencyRecord): CurrencyRecord {
        if(db.currency.any { it.name eq currencyRecord.name }) throw ValidationError("Валюта \"${currencyRecord.name}\" уже существует")
        db.currency.add(currencyRecord)
//        log.info("Добавлена новая валюта \"${currencyRecord.name}\"")
        return db.currency.find { it.name eq currencyRecord.name }!!
    }

    // валют обычно малое количество (до 50), поэтому разбивку по страницам не делаем
    fun getCurrencies(): List<CurrencyRecord> {
        return db.currency.map { it }
    }

    fun getCurrencyById(id: Int): CurrencyRecord? {
        return db.currency.find { it.id eq id }
    }

    fun getCurrencyByCode(code: String): CurrencyRecord? {
        return db.currency.find { it.code eq code }
    }

    fun getCurrencyByXmlCode(xmlCode: String): CurrencyRecord? {
        return db.currency.find { it.xmlCode eq xmlCode }
    }

    fun getCurrenciesByIds(ids: Set<Int>): List<CurrencyRecord> {
        return db.from(Currencies)
            .select()
            .where { Currencies.id inList ids }
            .map { Currencies.createEntity(it) }
    }

    fun getCurrencyNames(ids: List<Int>): List<String> {
        return db.from(Currencies)
            .select(Currencies.name)
            .where { Currencies.id inList ids }
            .map { it[Currencies.name]!! }
    }

    fun getCurrencyNamesMap(): Map<Int, String> {
        return db.from(Currencies)
            .select(Currencies.id, Currencies.name)
            .map { it[Currencies.id]!! to it[Currencies.name]!! }.toMap()
    }

    fun updateCurrency(currencyRecord: CurrencyRecord) {
        db.currency.update(currencyRecord)
//        log.info("Обновлена валюта \"${currencyRecord.name}\" (id=${currencyRecord.id})")
    }

    fun deleteCurrency(currencyRecordId: Int) {
        val currencyRecord = db.currency.find { it.id eq currencyRecordId }
        db.delete(Currencies) { it.id eq currencyRecordId }
//        log.info("Удалена валюта \"${currencyRecord?.name}\" (id=${currencyRecordId})")
    }

    fun getReserves(): List<Reserve> {
        return db.reserves.map { it }
    }

    fun getReserve(currency: String): Reserve? {
        return db.reserves.find { it.currency eq currency }
    }

    fun upsertReserve(reserve: Reserve) {
        reserve.lastUpdated = LocalDateTime.now()
        db.reserves.find { it.currency eq reserve.currency }?.let {
            it.lastUpdated = reserve.lastUpdated
            it.currency = reserve.currency
            it.reserveCurrency = reserve.reserveCurrency
            it.reserveType = reserve.reserveType
            it.value = reserve.value
            it.exchangeName = reserve.exchangeName
            db.reserves.update(it)
            return
        }

        db.reserves.add(reserve)
    }

    fun deleteReserve(currencyCode: String) {
        db.delete(Reserves) { it.currency eq currencyCode }
    }
}

interface CurrencyRecord: Entity<CurrencyRecord> {
    companion object : Entity.Factory<CurrencyRecord>()
    var id: Int
    var name: String
    var code: String
    var xmlCode: String
    var fidelity: Int
    var acctValidator: String
    var acctChain: String
    var isEnabled: Boolean
    var payin: String
    var payinCode: String
    var payinChain: String
    var payout: String
    var payoutCode: String
    var payoutChain: String
}

interface Reserve: Entity<Reserve> {
    companion object : Entity.Factory<Reserve>()
    var id: Int
    var currency: String
    var reserveCurrency: String
    var reserveType: String
    var value: Double
    var exchangeName: String
    var lastUpdated: LocalDateTime
}

interface CurrencyGiveFieldMapping : Entity<CurrencyGiveFieldMapping> {
    companion object : Entity.Factory<CurrencyGiveFieldMapping>()
    var currency: CurrencyRecord // Foreign key to CurrencyRecord
    var field: CurrencyField // Foreign key to CurrencyField
}

interface CurrencyGetFieldMapping : Entity<CurrencyGetFieldMapping> {
    companion object : Entity.Factory<CurrencyGetFieldMapping>()
    var currency: CurrencyRecord
    var field: CurrencyField
}

@Serializable
data class CurrencyRecordDTO(
    val id: Int = 0,
    val name: String = "",
    val code: String = "",
    val xmlCode: String = "",
    val fidelity: Int = 2,
//    val reserve: Double = 0.0,
//    val reserveFrom: String = "infinite", //тип резерва - из файла, с биржи, задать вручную, бесконечный
//    val reserveCurrency: Int = 0,
    val acctValidator: String = "",
    val acctChain: String = "",
    val isEnabled: Boolean = true,
    val payin: String,
    val payinCode: String,
    val payinChain: String,
    val payout: String,
    val payoutCode: String,
    val payoutChain: String
)

fun CurrencyRecord.toDTO(): CurrencyRecordDTO {
    return CurrencyRecordDTO(
        id = this.id,
        name = this.name,
        code = this.code,
        xmlCode = this.xmlCode,
        fidelity = this.fidelity,
        acctValidator = this.acctValidator,
        acctChain = this.acctChain,
        isEnabled = this.isEnabled,
        payin = this.payin,
        payinCode = this.payinCode,
        payinChain = this.payinChain,
        payout = this.payout,
        payoutCode = this.payinCode,
        payoutChain = this.payoutChain
    )
}

fun CurrencyRecordDTO.toDB(): CurrencyRecord {
    return CurrencyRecord {
        id = this@toDB.id
        name = this@toDB.name
        code = this@toDB.code
        xmlCode = this@toDB.xmlCode
        fidelity = this@toDB.fidelity
        acctValidator = this@toDB.acctValidator
        acctChain = this@toDB.acctChain
        isEnabled = this@toDB.isEnabled
        payin = this@toDB.payin
        payinCode = this@toDB.payinCode
        payinChain = this@toDB.payinChain
        payout = this@toDB.payout
        payoutCode = this@toDB.payoutCode
        payoutChain = this@toDB.payoutChain
    }
}