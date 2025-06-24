package com.github.perelshtein.database

import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.generatePreview
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
import org.ktorm.entity.count
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.update
import org.ktorm.schema.Table
import org.ktorm.schema.datetime
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.getValue

class NewsManager: KoinComponent {
    object News : Table<NewsRecord>("news") {
        val id = int("id").primaryKey().bindTo { it.id }
        val date = datetime("date").bindTo { it.date }
        val caption = varchar("caption").bindTo { it.caption }
        val text = varchar("text").bindTo { it.text }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val log = LoggerFactory.getLogger("NewsManager")
    val Database.news get() = this.sequenceOf(News)

    fun addNews(newsRecord: NewsRecord) {
        db.news.add(newsRecord)
        log.info("Добавлена новость \"${newsRecord.caption}\"")
    }

    fun getNewsCount(): Int {
        return db.news.count()
    }

//    fun getNews(from: Int = 0, count: Int = 10, textSize: Int = 0): List<NewsRecord> {
//        return db.useConnection { conn ->
//            conn.prepareStatement(
//                """
//            SELECT *
//            FROM news
//            ORDER BY date DESC
//            LIMIT ? OFFSET ?
//            """.trimIndent()
//            ).use { statement ->
//                statement.setInt(1, count) // "count" elements to take
//                statement.setInt(2, from)  // "from" elements to skip
//
//                statement.executeQuery().use { rs ->
//                    val records = mutableListOf<NewsRecord>()
//                    while (rs.next()) {
//                        val news = NewsRecord { // Populate NewsRecord entity
//                            id = rs.getInt("id")
//                            date = rs.getTimestamp("date").toLocalDateTime()
//                            caption = rs.getString("caption")
//                            text = if (textSize > 0) {
//                                rs.getString("text").generatePreview(textSize)
//                            } else ""
//                        }
//                        records.add(news)
//                    }
//                    records
//                }
//            }
//        }
//    }

    fun getNews(from: Int = 0, count: Int = 10, textSize: Int = 0): Pair<Int, List<NewsRecord>> {
        return db.useConnection { conn ->
            conn.prepareStatement(
                """
            SELECT *, COUNT(*) OVER() as total_count
            FROM news
            ORDER BY date DESC
            LIMIT ? OFFSET ?
            """.trimIndent()
            ).use { statement ->
                statement.setInt(1, count) // LIMIT
                statement.setInt(2, from)  // OFFSET

                statement.executeQuery().use { rs ->
                    val records = mutableListOf<NewsRecord>()
                    var totalCount = 0

                    while (rs.next()) {
                        // Extract total count from the first row
                        if (records.isEmpty()) {
                            totalCount = rs.getInt("total_count")
                        }

                        val news = NewsRecord {
                            id = rs.getInt("id")
                            date = rs.getTimestamp("date").toLocalDateTime()
                            caption = rs.getString("caption")
                            text = if (textSize > 0) {
                                rs.getString("text").generatePreview(textSize)
                            } else ""
                        }
                        records.add(news)
                    }

                    totalCount to records
                }
            }
        }
    }

    // при удалении новостей загружаем только заголовки, чтобы не запрашивать все данные вместе с текстом
    fun getNewsCaption(ids: List<Int>): List<String> {
        return db
            .from(News)
            .select(News.caption)
            .where { News.id inList ids }
            .map { row -> row[News.caption]!! }
    }

    fun getNewsRecord(id: Int): NewsRecord? {
        return db.news.find { it.id eq id }
    }

    fun updateNews(newsRecord: NewsRecord) {
        db.news.update(newsRecord)
        log.info("Обновлена новость \"${newsRecord.caption}\" (id=${newsRecord.id})")
    }

    fun deleteNews(newsRecordId: Int) {
        val newsRecord = db.news.find { it.id eq newsRecordId }
        db.delete(News) { it.id eq newsRecordId }
        log.info("Удалена новость \"${newsRecord?.caption}\" (id=${newsRecordId})")
    }
}

interface NewsRecord: Entity<NewsRecord> {
    companion object : Entity.Factory<NewsRecord>()
    var id: Int
    var date: LocalDateTime
    var caption: String
    var text: String
}

fun NewsRecord.toDTO(): NewsRecordDTO {
    return NewsRecordDTO(
        id = this.id,
        date = this.date,
        caption = this.caption,
        text = this.text
    )
}

fun NewsRecordDTO.toDB(): NewsRecord {
    return NewsRecord {
        id = this@toDB.id
        date = this@toDB.date
        caption = this@toDB.caption
        text = this@toDB.text
    }
}

@Serializable
data class NewsRecordDTO(
    val id: Int = 0,
    @Serializable(with = LocalDateTimeSerializer::class) val date: LocalDateTime = LocalDateTime.now(),
    val caption: String,
    val text: String
)