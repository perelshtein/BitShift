package com.github.perelshtein.database

import com.github.perelshtein.database.DatabaseAccess
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.mapNotNull
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.ktorm.schema.int
import org.ktorm.schema.Table
import org.slf4j.LoggerFactory

class CurrencyFieldsBindManager(type: String): KoinComponent {
    sealed class CurrencyFieldsBind(tableName: String) : Table<Nothing>(tableName) {
        val id = int("id").primaryKey()
        val currencyId = int("currency_id")
        val fieldId = int("field_id")
    }
    object CurrencyFieldsGet : CurrencyFieldsBind("currency_get_fields")
    object CurrencyFieldsGive : CurrencyFieldsBind("currency_give_fields")
    val table = if(type == "get") CurrencyFieldsGet
        else if(type == "give") CurrencyFieldsGive
        else throw Exception("Задайте таблицу give или get")

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val log = LoggerFactory.getLogger("CurrencyFieldsBindManager")

    fun add(field: CurrencyFieldBindDTO) {
        db.insert(table) {
            set(it.currencyId, field.currencyId)
            set(it.fieldId, field.fieldId)
        }
    }

    fun getFieldsByCurrency(currencyId: Int): List<CurrencyFieldBindDTO> {
        return db.from(table)
            .select()
            .where { table.currencyId eq currencyId }
            .map {
                CurrencyFieldBindDTO(
                    id = it[table.id]!!,
                    currencyId = it[table.currencyId]!!,
                    fieldId = it[table.fieldId]!!
                )
            }
    }

    fun setFieldsByCurrency(currencyIdToUpdate: Int, fieldIds: List<Int>) {
        db.delete(table) { table.currencyId eq currencyIdToUpdate }
        fieldIds.forEach {
            add(CurrencyFieldBindDTO(
                currencyId = currencyIdToUpdate,
                fieldId = it
            ))
        }
    }

    fun getCurrenciesByField(fieldId: Int): List<Int> {
        return db.from(table)
            .select(table.currencyId)
            .where { table.fieldId eq fieldId }
            .mapNotNull { row -> row[table.currencyId]?.toInt() }
    }

    fun setCurrenciesByField(fieldIdToUpdate: Int, currencyIds: List<Int>) {
        db.delete(table) { table.fieldId eq fieldIdToUpdate }
        currencyIds.forEach {
            add(CurrencyFieldBindDTO(
                currencyId = it,
                fieldId = fieldIdToUpdate)
            )
        }
    }

    fun getFields(): List<CurrencyFieldBindDTO> {
        return db.from(table)
            .select()
            .map {
                CurrencyFieldBindDTO(
                    id = it[table.id]!!,
                    currencyId = it[table.currencyId]!!,
                    fieldId = it[table.fieldId]!!
                )
            }
    }

//    fun delete(ids: List<Int>) {
//        db.delete(table) { table.id inList ids }
//    }
}

@Serializable
data class CurrencyFieldBindDTO(
    val id: Int = 0, // при сохранении в базу id не нужен. он генерируется базой
    val currencyId: Int,
    val fieldId: Int
)