package com.github.perelshtein

import at.favre.lib.crypto.bcrypt.BCrypt
import com.github.perelshtein.database.CashbackOrder
import com.github.perelshtein.database.CourseDTO
import com.github.perelshtein.database.DirectionShortDTO
import com.github.perelshtein.database.FormulaDTO
import com.github.perelshtein.database.NewsRecordDTO
import com.github.perelshtein.database.OrderAdminDTO
import com.github.perelshtein.database.OrderUserDTO
import com.github.perelshtein.database.ReferralOrder
import com.github.perelshtein.database.ReviewsAdminDTO
import com.github.perelshtein.database.ReviewsPublicDTO
import com.github.perelshtein.routes.LoginInfo
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.java.KoinJavaComponent
import java.math.RoundingMode
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.text.split
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import nl.adaptivity.xmlutil.serialization.serializer
import kotlin.math.ceil

fun String.generateRandom(): String {
    return (1..16)
        .map { Random.nextInt(0, 10) }
        .joinToString("")
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.format(formatter))
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString(), formatter)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        LocalDateTime::class.java.canonicalName,
        PrimitiveKind.STRING
    )
}

fun Route.checkIds(call: ApplicationCall, namePlural: String, parameter: String = "ids"): List<Int> {
    val ids = call.request.queryParameters[parameter]
        ?.split(",")
        ?.mapNotNull { it.toIntOrNull() } // Переводим в числа, пропускаем некорректные значения

    if (ids.isNullOrEmpty()) throw ValidationError("Не указаны ${namePlural}. Укажите их через ?${parameter}=123,456")
    if (ids.size != call.request.queryParameters["ids"]?.split(",")?.size) {
        val nameReplaced = namePlural
            .replaceLast("и", "ей")
            .replaceLast("ы", "")
            .replaceLast("я", "ей") //TODO: найти норм библиотеку для падежей
        throw ValidationError("Некорректные id ${nameReplaced}. Формат должен быть числовым.")
    }
    return ids
}

fun String.replaceLast(oldValue: String, newValue: String): String {
    val lastIndex = this.lastIndexOf(oldValue)
    return if (lastIndex >= 0) {
        this.substring(0, lastIndex) + newValue + this.substring(lastIndex + oldValue.length)
    } else {
        this // If oldValue is not found, return the original string
    }
}

fun String.checkURL(): Boolean {
    return try {
        URI(this).toURL()
        true
    } catch (e: Exception) {
        false
    }
}

fun String.checkEmail(): Boolean {
    val filter = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
    return this.trim().matches(filter)
}

fun String.replaceTags(replacements: Map<String, String>): String {
    val pattern = "\\[([^\\[\\]]+)\\]".toRegex() // Matches [anything] but not nested brackets
    return pattern.replace(this) { matchResult ->
        val key = matchResult.groupValues[1] // Extracts content inside brackets
        replacements[key] ?: matchResult.value // Replace with value or keep original if key not found
    }
}

fun String.generateHash(): String {
    return BCrypt.withDefaults().hashToString(12, this.toCharArray()) // 12 - уровень сложности шифрования
}

fun String.generatePreview(maxLength: Int): String {
    if (this.length <= maxLength) return this

    // Find the last space before maxLength
    val lastSpaceIndex = this.substring(0, maxLength).lastIndexOf(' ')

    // If no space is found, or the last space is too far back, crop at maxLength
    return if (lastSpaceIndex == -1 || lastSpaceIndex < maxLength * 0.5) {
        this.substring(0, maxLength).trimEnd() + "..."
    } else {
        this.substring(0, lastSpaceIndex).trimEnd() + "..."
    }
}

// округляем до N цифр после запятой
fun Double.roundup(digits: Int): Double {
    return this.toBigDecimal().setScale(digits, RoundingMode.HALF_UP).toDouble()
}

//округляем до мин. шага (0.001, 10, 100)
fun Double.roundToStep(step: Double): Double {
    val digits = if (step < 1) -kotlin.math.log10(step).toInt() else 0
    return (ceil(this / step) * step).roundup(digits)
}

// для входящих запросов из веб-форм
// при получении Map удаляем кавычки, пробелы и табуляцию из JSON
fun Map<String, JsonElement>.trimmed(): Map<String, JsonElement?> = mapValues { (_, value) ->
    when {
        value is JsonNull -> null
        value is JsonPrimitive && value.isString -> {
            JsonPrimitive(value.content.trim(' ', '\t', '"'))
        }
        else -> value
    }
}

class Hashing(secretKey: String): KoinComponent {
    val hasher = Mac.getInstance("HmacSHA256")
    val secretKeyBytes = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA256")
    init {
        hasher.init(secretKeyBytes)
    }

    fun encode(input: String): Result<String> {
        runCatching {
            val hash = hasher.doFinal(input.toByteArray(Charsets.UTF_8))
            return Result.success(bytesToHex(hash))
        }.getOrElse {
            return Result.failure(it)
        }
    }

    private fun bytesToHex(hash: ByteArray): String {
        val hexString = StringBuilder()
        for (b in hash) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }
}

fun ApplicationCall.getUserId(): Int? {
    val sessionManager: SessionManager = KoinJavaComponent.get(SessionManager::class.java)
    return request.cookies[COOKIE_AUTH_TOKEN]?.let { token ->
        sessionManager.getUserIdByToken(token)
    }
}

fun ApplicationCall.getRealIP(): String = request.headers["CF-Connecting-IP"] ?: request.origin.remoteAddress

fun ApplicationCall.getUsername(): String? {
    val sessionManager: SessionManager = KoinJavaComponent.get(SessionManager::class.java)
    val accessControl: AccessControl = KoinJavaComponent.get(AccessControl::class.java)
    return request.cookies[COOKIE_AUTH_TOKEN]?.let { token ->
        sessionManager.getUserIdByToken(token)?.let { userId ->
            accessControl.getUserById(userId)?.name
        }
    }
}

object DoubleOrZeroSerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("DoubleOrZero", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder) = decoder.decodeString().toDoubleOrNull() ?: 0.0
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

// для отправки ответов с динамическим числом полей,
// когда data-class создавать не имеет смысла
// (если такой формат используется один раз)
fun jsonMap(
     vararg pairs: Pair<String, Any?>
): JsonObject {
    val jsonMap = pairs.filter { it.second != null }
        .associate { (key, value) ->
            key to when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString()) // вложенные объекты не прокатят
            }
        }
    return JsonObject(jsonMap)
}

//@Serializable
//sealed class ApiResponse<T> {
//    @Serializable
//    @SerialName("success")
//    data class Success<T>(
//        val message: String = "OK",
//        val data: T? = null,
//        val action: ACTION? = null
//    ) : ApiResponse<T>()
//
//    @Serializable
//    @SerialName("warning")
//    data class Warning<T>(
//        val message: String? = null,
//        val data: T? = null,
//        val action: ACTION? = null
//    ) : ApiResponse<T>()
//
//    @Serializable
//    @SerialName("error")
//    data class Error<T>(
//        val message: String? = null,
//        val data: T? = null,
//        val action: ACTION? = null
//    ): ApiResponse<T>()
//}

@Serializable
data class ApiResponse<T>(
    val type: String,
    val message: String? = null,
    val data: T? = null,
    val action: ACTION? = null
) {
    companion object {
        fun <T> Success(
            message: String? = null,
            data: T? = null,
            action: ACTION? = null
        ): ApiResponse<T> = ApiResponse(
            type = "success",
            message = message,
            data = data,
            action = action
        )

        fun <T> Warning(
            message: String? = null,
            data: T? = null,
            action: ACTION? = null
        ): ApiResponse<T> = ApiResponse(
            type = "warning",
            message = message,
            data = data,
            action = action
        )

        fun <T> Error(
            message: String,
            data: T? = null,
            action: ACTION? = null
        ): ApiResponse<T> = ApiResponse(
            type = "error",
            message = message,
            data = data,
            action = action
        )
    }
}

@Serializable
enum class ACTION {
    LOGIN, LOGOUT, WEBSITE, ADMIN, CHECKUP_ORDER, ORDER_BY_ID
}

suspend inline fun <reified T> ApplicationCall.respondApi(
    response: ApiResponse<T>,
    statusCode: HttpStatusCode = HttpStatusCode.OK
) {
    respond(statusCode, response)
}

@Serializable
data class Pagination<T>(
    val items: List<T>,
    val total: Int,
    val page: Int, //нумерация начинается с 1
    val pageSize: Int //
)