package com.github.perelshtein.routes

import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.database.DirectionsManager
import com.github.perelshtein.withUser
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

fun Route.courseXml() {
    get("/course.xml") {
        val log = LoggerFactory.getLogger("Route.courseXml")
        runCatching {
            val mgr: DirectionsManager = KoinJavaComponent.get(DirectionsManager::class.java)
            call.response.headers.append("Content-Type", "application/xml")
            call.respond(mgr.getDirectionsXML(), typeInfo<Rates>())
        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}

@Serializable
@XmlSerialName("rates")
data class Rates(
    @SerialName("item")
    @XmlElement(true)
    val items: List<Item>
)

@Serializable
@XmlElement(true)
@XmlSerialName("item")
data class Item(
    @SerialName("from")
    @XmlElement(true)
    val from: String,

    @SerialName("to")
    @XmlElement(true)
    val to: String,

    @SerialName("in")
    @XmlElement(true)
    val giveCourse: Double,

    @SerialName("out")
    @XmlElement(true)
    val getCourse: Double,

    @SerialName("amount")
    @XmlElement(true)
    val amount: Double,

    @SerialName("minamount")
    @XmlElement(true)
    val minAmount: Double,

    @SerialName("maxamount")
    @XmlElement(true)
    val maxAmount: Double,

    @SerialName("param")
    @XmlElement(true)
    val param: String?
)