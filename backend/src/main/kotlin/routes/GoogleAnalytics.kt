package com.github.perelshtein.routes

import com.github.perelshtein.ApiResponse
import com.github.perelshtein.respondApi
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

fun Route.googleAnalytics() {
    get("/gtag/js") {
        val log = LoggerFactory.getLogger("Route.googleAnalytics")
        val client: HttpClient = KoinJavaComponent.get(HttpClient::class.java)
        val measurementId = call.parameters["id"]
        if(measurementId == null) {
            call.respondApi<Unit>(
                response = ApiResponse.Error(message = "Missing id parameter"),
                statusCode = HttpStatusCode.BadRequest
            )
            return@get
        }
        try {
            val response = client.get("https://www.googletagmanager.com/gtag/js?id=$measurementId") {
                headers {
                    // Forward relevant headers from the client
                    call.request.headers.forEach { key, values ->
                        if (key !in listOf(HttpHeaders.Host, HttpHeaders.ContentLength)) {
                            appendAll(key, values)
                        }
                    }
                }
            }

            call.respondBytes(
                contentType = ContentType.parse(response.contentType()?.toString() ?: "application/javascript"),
                status = HttpStatusCode.fromValue(response.status.value),
                bytes = response.bodyAsBytes()
            )
        } catch (e: Exception) {
            val msg = "Proxy error: ${e.message}"
            log.error(msg, e)
            call.respondApi<Unit>(
                response = ApiResponse.Error(message = msg),
                statusCode = HttpStatusCode.InternalServerError
            )
        }
    }
}