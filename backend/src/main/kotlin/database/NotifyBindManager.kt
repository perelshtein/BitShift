package com.github.perelshtein.database

import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.ktorm.schema.Table
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory

class NotifyBindManager(type: String): KoinComponent {
    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    private val log = LoggerFactory.getLogger("NotifyBindManager")

    sealed class NotifyUserBind(tableName: String) : Table<Nothing>(tableName) {
        val key = varchar("field_name").primaryKey()
        val subject = varchar("subject")
        val text = varchar("text")
    }
    object NotifyAdmin: NotifyUserBind("notify_admin")
    object NotifyUser: NotifyUserBind("notify_users")
    val table = if(type == "admin") NotifyAdmin
        else if(type == "user") NotifyUser
        else throw Exception("Задайте таблицу admin или user")

    init {
        if(type == "user" && db.from(table).select().totalRecordsInAllPages == 0) {
            listOf(
                NotifyDTO("cancelled","Заявка id=[id]","Заявка id=[id] отменена"),
                NotifyDTO("completed","Заявка id=[id]","<p>Заявка id=[id] выполнена.</p>\n<p>[amountFrom] [currencyFrom] -> [amountTo] [currencyTo]</p>\n[requisites]\nСпасибо за доверие. Будем рады вас видеть снова!\n"),
                NotifyDTO("deleted","Заявка id=[id]","Заявка id=[id] удалена"),
                NotifyDTO("error","Заявка id=[id]","Нам очень жаль.\nПроизошла ошибка по заявке id=[id].\nНапишите в поддержку через <a href=\"tg://resolve?domain=e_bitshift\">Телеграм</a> или <a href=\"mailto:e@bitshift.su\">по эл.почте</a>, мы найдем причину и все починим."),
                NotifyDTO("new","Заявка id=[id]","Новая заявка id=[id] на BitShift!\n<p>[amountFrom] [currencyFrom] -> [amountTo] [currencyTo]</p>\nСрок обработки - до 30мин"),
                NotifyDTO("newUser","Регистрация на BitShift","<p>Нажмите <a href=[register]>здесь</a>, чтобы подтвердить регистрацию в BitShift.</p>"),
                NotifyDTO("orderNew","",""),
                NotifyDTO("payed","Заявка id=[id]","Заявка id=[id] оплачена.\n<p>[amountFrom] [currencyFrom] -> [amountTo] [currencyTo]</p>\nСо счета: [walletFrom]\n<p>На счет: [walletTo]</p>\n[requisites]"),
                NotifyDTO("registrationSuccess","Добро пожаловать на BitShift","<p>Регистрация в BitShift завершена!</p>\n<p>Ваш логин: [login]</p>\n<p>Ваш пароль: [password]</p>")
            ).forEach { upsertStatus(it) }
        }
        else if(type == "admin" && db.from(table).select().totalRecordsInAllPages == 0) {
            listOf(
                NotifyDTO("cancelled","Заявка id=[id]","Заявка id=[id] отменена."),
                NotifyDTO("completed","Заявка id=[id]","Заявка id=[id] выполнена.\nприбыль [profit]% ([profitPercent] USDT)"),
                NotifyDTO("deleted","Заявка id=[id]","Заявка id=[id] удалена."),
                NotifyDTO("error","Заявка id=[id]","Ошибка по заявке id=[id]."),
                NotifyDTO("new","Заявка id=[id]","Новая заявка id=[id]\nПочта: [email]\n[amountFrom] [currencyFrom] -> [amountTo] [currencyTo]\n[receiveType] - [sendType]")
            ).forEach { upsertStatus(it) }
        }
    }

    fun getStatus(key: String): NotifyDTO? {
        return db.from(table)
            .select()
            .where { table.key eq key }.map {
                NotifyDTO(
                    key = it[table.key]!!,
                    subject = it[table.subject]!!,
                    text = it[table.text]!!
                )
            }
            .firstOrNull()
    }

    fun getStatuses(): List<NotifyDTO> {
        return db.from(table)
            .select()
            .map {
                NotifyDTO(
                    key = it[table.key]!!,
                    subject = it[table.subject]!!,
                    text = it[table.text]!!
                )
            }
    }

    fun upsertStatuses(notify: List<NotifyDTO>) {
        notify.forEach { upsertStatus(it) }
    }

    fun upsertStatus(notify: NotifyDTO) {
        db.delete(table) { table.key eq notify.key }
        db.insert(table) {
            set(it.key, notify.key)
            set(it.subject, notify.subject)
            set(it.text, notify.text)
        }
        log.debug("Добавлен статус ${notify.key}")
    }
}

@Serializable
data class NotifyDTO(
    var key: String,
    var subject: String,
    var text: String
)