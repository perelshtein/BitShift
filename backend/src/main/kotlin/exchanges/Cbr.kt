package com.github.perelshtein.exchanges
import com.github.perelshtein.database.Exchange
import com.github.perelshtein.database.Exchange.Companion.invoke
import com.github.perelshtein.database.ExchangeManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.getValue

class Cbr: KoinComponent, ExchangeAPI {
    val mgr: ExchangeManager by inject()
    val client: HttpClient by inject()
    val CBR = Cbr::class.simpleName!!
    val log = LoggerFactory.getLogger("Cbr")
    private var exchangeInfo: Exchange

    init {
        if(mgr.getExchangeByName(CBR) == null) {
            mgr.addExchange(Exchange {
                name = CBR
                updatePeriod = 15
                maxFailCount = 5
                isEnabled = true
                url = "https://www.cbr.ru"
                lastUpdate = LocalDateTime.now().minusMinutes(2L)
            })
        }
        exchangeInfo = mgr.getExchangeByName(CBR)!!
    }

    override suspend fun fetchCodes(): Set<String> {
        val response = client.get("${exchangeInfo.url}/scripts/XML_daily.asp")
        if (response.status != HttpStatusCode.OK) throw Exception("Код ошибки HTTP: ${response.status}")
        val rates = response.body<ValCurs>()
        return rates.Valute
            .map { replaceCode(it.charCode) }
            .toSet() + setOf("RUB")
    }

    override suspend fun fetchCourses(): List<Course> {
        val response = client.get("${exchangeInfo.url}/scripts/XML_daily.asp")
        if (response.status != HttpStatusCode.OK) throw Exception("Код ошибки HTTP: ${response.status}")
        val rates = response.body<ValCurs>()
        val v = rates.Valute
            .map {
                Course(from = replaceCode(it.charCode), to = "RUB", price = it.value.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    buy = 0.0, sell = 0.0)
            }
        return v
    }

    private fun replaceCode(code: String): String {
        val c = code.uppercase()
        return when(c) {
            "USD" -> "USDT"
            else -> c
        }
    }
}

@Serializable
@XmlSerialName("ValCurs") // Root element name
data class ValCurs(
    @SerialName("Date")
    val date: String,

    @SerialName("name")
    val name: String? = null, // Optional, nullable

    @XmlElement(true)
    val Valute: List<Valute> // Child elements named "Valute"
)

@Serializable
@XmlSerialName("Valute")
data class Valute(
    @SerialName("ID")
    val id: String,

    @XmlElement(true)
    @SerialName("NumCode")
    val numCode: String,

    @XmlElement(true)
    @SerialName("CharCode")
    val charCode: String,

    @XmlElement(true)
    @SerialName("Nominal")
    val nominal: Int,

    @XmlElement(true)
    @SerialName("Name")
    val name: String,

    @XmlElement(true)
    @SerialName("Value")
    val value: String // String due to comma (e.g., "91,2345")
)