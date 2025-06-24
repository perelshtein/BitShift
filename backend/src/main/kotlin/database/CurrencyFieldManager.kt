package com.github.perelshtein.database

import com.github.perelshtein.database.DatabaseAccess
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
import org.ktorm.entity.map
import org.ktorm.entity.update
import org.ktorm.entity.sequenceOf
import org.ktorm.schema.Table
import org.ktorm.schema.boolean
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import kotlin.getValue

class CurrencyFieldManager: KoinComponent {
    object CurrencyFields : Table<CurrencyField>("currency_fields") {
        val id = int("id").primaryKey().bindTo { it.id }
        val name = varchar("name").bindTo { it.name }
        val isRequired = boolean("is_required").bindTo { it.isRequired }
        val hintAccountFrom = varchar("hint_account_from").bindTo { it.hintAccountFrom }
        val hintAccountTo = varchar("hint_account_to").bindTo { it.hintAccountTo }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val log = LoggerFactory.getLogger("CurrencyFieldManager")
    val Database.currencyFields get() = this.sequenceOf(CurrencyFields)

    fun add(field: CurrencyField): CurrencyField {
        if (db.currencyFields.any { it.name eq field.name }) throw ValidationError("Поле \"${field.name}\" уже существует")
        db.currencyFields.add(field)
        log.info("Добавлено поле для валют: \"${field.name}\"")
        return db.currencyFields.find { it.name eq field.name }!!
    }

    fun update(field: CurrencyField) {
        db.currencyFields.update(field)
        log.info("Обновлено поле для валют: \"${field.name}\"")
    }

    fun getFields(): List<CurrencyField> {
        return db.currencyFields.map { it }
    }

    fun getFieldCaption(ids: List<Int>): List<String> {
        return db.from(CurrencyFields)
            .select(CurrencyFields.name)
            .where { CurrencyFields.id inList ids }
            .map { it[CurrencyFields.name]!! }
    }

    fun delete(id: Int) {
        val field = db.currencyFields.find { it.id eq id }
        db.delete(CurrencyFields) { it.id eq id }
        log.info("Удалено поле для валют: \"${field?.name}\"")
    }
}

interface CurrencyField : Entity<CurrencyField> {
    companion object : Entity.Factory<CurrencyField>()
    var id: Int
    var name: String // название поля, например, ФИО (полностью)
    var isRequired: Boolean
    var hintAccountFrom: String
    var hintAccountTo: String
}

fun CurrencyField.toDTO(): CurrencyFieldDTO {
    return CurrencyFieldDTO(
        id = this.id,
        name = this.name,
        isRequired = this.isRequired,
        hintAccountFrom = this.hintAccountFrom,
        hintAccountTo = this.hintAccountTo
    )
}

fun CurrencyFieldDTO.toDB(): CurrencyField {
    return CurrencyField {
        id = this@toDB.id
        name = this@toDB.name
        isRequired = this@toDB.isRequired
        hintAccountFrom = this@toDB.hintAccountFrom
        hintAccountTo = this@toDB.hintAccountTo
    }
}

@Serializable
data class CurrencyFieldDTO(
    val id: Int = 0,
    val name: String = "",
    val isRequired: Boolean = false,
    val hintAccountFrom: String = "",
    val hintAccountTo: String = ""
)