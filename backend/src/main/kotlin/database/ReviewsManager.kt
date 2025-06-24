package com.github.perelshtein.database

import com.github.perelshtein.LocalDateTimeSerializer
import com.github.perelshtein.ValidationError
import com.github.perelshtein.generatePreview
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.deleteAll
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.greaterEq
import org.ktorm.dsl.insertAndGenerateKey
import org.ktorm.dsl.lessEq
import org.ktorm.dsl.like
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.whereWithConditions
import org.ktorm.entity.Entity
import org.ktorm.entity.firstOrNull
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.update
import org.ktorm.schema.Table
import org.ktorm.schema.datetime
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import org.ktorm.support.mysql.bulkInsert
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.getValue

class ReviewsManager: KoinComponent {
    object Reviews : Table<ReviewsRecord>("reviews") {
        val id = int("id").primaryKey().bindTo { it.id }
        val date = datetime("date").bindTo { it.date }
        val mail = varchar("mail").bindTo { it.mail }
        val caption = varchar("caption").bindTo { it.caption }
        val text = varchar("text").bindTo { it.text }
        val status = varchar("status").bindTo { it.status }
        val rating = int("rating").bindTo { it.rating }
    }

    object StopWords: Table<StopWordsRecord>("stop_words") {
        val text = varchar("text").primaryKey().bindTo { it.text }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val log = LoggerFactory.getLogger("ReviewsManager")
    val Database.reviews get() = this.sequenceOf(Reviews)
    val Database.stopWords get() = this.sequenceOf(StopWords)

    fun getReviews(start: Int = 0, count: Int = 10, textSize: Int = 2048, dateStart: LocalDateTime? = null, dateEnd: LocalDateTime? = null,
                   status: String? = null, caption: String? = null, mail: String? = null, text: String? = null, rating: Int? = null): Pair<Int, List<ReviewsRecord>> {
        val query = db.from(Reviews)
            .select()
            .whereWithConditions {
                if (dateStart != null) {
                    it += Reviews.date greaterEq dateStart
                }
                if (dateEnd != null) {
                    it += Reviews.date lessEq dateEnd
                }
                if (status != null) {
                    it += Reviews.status eq status
                }
                if (caption != null) {
                    it += Reviews.caption like "%$caption%"
                }
                if (text != null) {
                    it += Reviews.text like "%$text%"
                }
                if(mail != null) {
                    it += Reviews.mail like "%$mail%"
                }
                if(rating != null) {
                    it += Reviews.rating eq rating
                }
            }

        val countFound = query.totalRecordsInAllPages
        if (countFound == 0) return 0 to emptyList()
        val result = query
            .limit(start, count)
            .map { row ->
                val entity = Reviews.createEntity(row)
                if (textSize in 1..2048) {
                    entity.apply { this.text = entity.text.generatePreview(textSize) }
                } else {
                    entity
                }
            }
        return countFound to result
    }

    fun getReview(id: Int): ReviewsRecord? {
        return db.reviews.firstOrNull { it.id eq id }
    }

    fun getUserReview(id: Int, mail: String): ReviewsRecord? {
        return db.reviews.firstOrNull { (it.id eq id) and (it.mail eq mail) }
    }

    fun addReview(mail: String, caption: String, text: String, rating: Int): ReviewsRecord {
        val now = LocalDateTime.now()
        val id = db.insertAndGenerateKey(Reviews) {
            set(it.mail, mail)
            set(it.text, text)
            set(it.date, now)
            set(it.status, "moderation")
            set(it.caption, caption)
            set(it.rating, rating)
        } as Int
        val found = db.reviews.firstOrNull { it.id eq id }
        if(found == null) throw ValidationError("Ошибка при добавлении отзыва")
        return found
    }

    fun editReview(id: Int, text: String? = null, status: String? = null, mail: String? = null, rating: Int? = null): ReviewsRecord {
        val found = if(mail == null)  db.reviews.firstOrNull { it.id eq id }
        else db.reviews.firstOrNull { (it.id eq id) and (it.mail eq mail) }
        if(found == null) throw ValidationError("Отзыв не найден")
        val updated = found.apply {
            text?.let { this.text = it }
            status?.let { this.status = it }
            rating?.let { this.rating = it }
        }
        db.reviews.update(updated)
        return updated
    }

    fun getStopWords(): Set<String> {
        return db.stopWords
            .map { it.text }
            .toSortedSet()
    }

    fun saveStopWords(words: Set<String>) {
//        db.useTransaction {
            db.deleteAll(StopWords)
            db.bulkInsert(StopWords) {
                words.forEach { word ->
                    item {
                        set(it.text, word)
                    }
                }
            }
//        }
    }
}

interface ReviewsRecord: Entity<ReviewsRecord> {
    companion object : Entity.Factory<ReviewsRecord>()
    var id: Int
    var date: LocalDateTime
    var mail: String
    var caption: String
    var text: String
    var status: String
    var rating: Int
}

interface StopWordsRecord: Entity<StopWordsRecord> {
    companion object : Entity.Factory<StopWordsRecord>()
    var text: String
}

@Serializable
data class ReviewsAdminDTO(
    val id: Int,
    @Serializable
    val date: String,
    val mail: String,
    val caption: String,
    val text: String,
    val status: String,
    val rating: Int
)

@Serializable
data class ReviewsPublicDTO(
    val id: Int,
    @Serializable(LocalDateTimeSerializer::class)
    val date: LocalDateTime,
    val mail: String,
    val caption: String,
    val text: String,
    val rating: Int
)

@Serializable
data class ReviewsUserDTO(
    val id: Int,
    @Serializable(LocalDateTimeSerializer::class)
    val date: LocalDateTime,
    val caption: String,
    val text: String,
    val status: String,
    val rating: Int
)

fun ReviewsRecord.toAdminDTO(): ReviewsAdminDTO = ReviewsAdminDTO(
    id = id,
    date = date.toString(),
    mail = mail,
    caption = caption,
    text = text,
    status = status,
    rating = rating
)

fun ReviewsRecord.toPublicDTO(): ReviewsPublicDTO = ReviewsPublicDTO(
    id = id,
    date = date,
    mail = mail,
    caption = caption,
    text = text,
    rating = rating
)

fun ReviewsRecord.toUserDTO(): ReviewsUserDTO = ReviewsUserDTO(
    id = id,
    date = date,
    caption = caption,
    text = text,
    status = status,
    rating = rating
)