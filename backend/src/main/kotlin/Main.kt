package com.github.perelshtein
import com.github.perelshtein.database.CashbackManager
import com.github.perelshtein.database.CookieBanManager
import com.github.perelshtein.database.CourseManager
import com.github.perelshtein.database.CurrencyFieldManager
import com.github.perelshtein.database.CurrencyFieldsBindManager
import com.github.perelshtein.database.CurrencyManager
import com.github.perelshtein.database.DatabaseAccess
import com.github.perelshtein.database.DirectionsManager
import com.github.perelshtein.database.EncryptedStorage
import com.github.perelshtein.database.ExchangeManager
import com.github.perelshtein.database.FormulaManager
import com.github.perelshtein.database.NewsManager
import com.github.perelshtein.database.NotifyBindManager
import com.github.perelshtein.database.OrdersManager
import com.github.perelshtein.database.ReferralManager
import com.github.perelshtein.database.ReviewsManager
import com.github.perelshtein.database.VerifyLinkManager
import com.github.perelshtein.exchanges.*
import com.github.perelshtein.exchanges.UpdateCourses
import com.github.perelshtein.routes.ConvertPrice
import com.github.perelshtein.routes.LoginError
import com.github.perelshtein.routes.autoPay
import com.github.perelshtein.routes.cashback
import com.github.perelshtein.routes.courseXml
import com.github.perelshtein.routes.exchanges
import com.github.perelshtein.routes.currencies
import com.github.perelshtein.routes.currencyFields
import com.github.perelshtein.routes.directions
import com.github.perelshtein.routes.formulas
import com.github.perelshtein.routes.googleAnalytics
import com.github.perelshtein.routes.login
import com.github.perelshtein.routes.logout
import com.github.perelshtein.routes.news
import com.github.perelshtein.routes.notify
import com.github.perelshtein.routes.options
import com.github.perelshtein.routes.orders
import com.github.perelshtein.routes.putOrder
import com.github.perelshtein.routes.referrals
import com.github.perelshtein.routes.reviews
import com.github.perelshtein.routes.roles
import com.github.perelshtein.routes.users
import com.github.perelshtein.routes.verify
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.xml
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.condition
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.httpsredirect.*
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.serializersModuleOf
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.XML
import org.bitcoinj.crypto.X509Utils.loadKeyStore
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.get
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.time.LocalDateTime
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds

// в целях безопасности
// для этой куки поставим httpOnly, ее нельзя читать из JS
const val COOKIE_REF_NAME = "referral_session"
const val COOKIE_AUTH_TOKEN = "obmenAuthToken"
const val COOKIE_PRODUCTION = false // для вкл https и сборки боевого проекта поставить true
const val FRONT_PORT_DEV = 5173

@OptIn(ExperimentalXmlUtilApi::class)
val appModule = module {
    singleOf(::Options) //настройки из файла

    //работа с базой
    singleOf(::DatabaseAccess)
    singleOf(::AccessControl)
    singleOf(::NewsManager)
    singleOf(::SessionManager)
    singleOf(::OptionsManager) //настройки из базы
    singleOf(::CurrencyManager)
    singleOf(::CurrencyFieldManager)
    singleOf(::FormulaManager)
    single(named("giveFields")) { CurrencyFieldsBindManager("give") }
    single(named("getFields")) { CurrencyFieldsBindManager("get") }
    single(named("admin")) { NotifyBindManager("admin") }
    single(named("user")) { NotifyBindManager("user") }
    singleOf(::CurrencyValidator)
    singleOf(::CourseManager)
    singleOf(::ExchangeManager)
    singleOf(::UpdateCourses)
    singleOf(::DirectionsManager)
    factoryOf(::EncryptedStorage)
    singleOf(::ReserveFormatter)
    singleOf(::OrdersManager)
    singleOf(::CookieBanManager)
    singleOf(::Mail)
    singleOf(::VerifyLinkManager)
    singleOf(::TempOrderStorage)
    singleOf(::ReferralManager)
    singleOf(::CashbackManager)

    singleOf(::Bybit)
    singleOf(::Binance)
//    singleOf(::Garantex)
    singleOf(::CoinMarketCap)
    singleOf(::Cbr)
    singleOf(::Mexc)
    singleOf(::Dijkstra)
    singleOf(::CrossCourses)
    singleOf(::ConvertPrice)
    singleOf(::Autopay)
    singleOf(::TelegramBot)
    singleOf(::NotifySender)
    singleOf(::LimitChecker)
    singleOf(::ReviewsManager)
//    factoryOf(::Hashing)
    factory { (key: String) -> Hashing(key) }

    single {
        HttpClient(OkHttp) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
                xml(
                    XML {
                        defaultPolicy {
                            ignoreUnknownChildren() // Ignores unknowns
                        }
                    }
                )
            }
        }
    }

    singleOf(::CookieGen)
}

fun startKoinModules() {
    startKoin {
        modules(appModule)
    }
}

fun main (args: Array<String>) {
    //выставим часовой пояс, если нужно
    val optFromFile = Options()
    val systemTimeOffset = TimeZone.getDefault().getRawOffset() / (60 * 1000)
    if(optFromFile.timezoneOffsetMinutes != systemTimeOffset) {
        val timezoneId = getTimeZoneIDFromOffset(optFromFile.timezoneOffsetMinutes)
        println("Выставляю часовой пояс приложения: ${timezoneId}")
        TimeZone.setDefault(TimeZone.getTimeZone(timezoneId))
    }

    System.setProperty("logback.configurationFile", "logback.xml")
    val log = LoggerFactory.getLogger("Main.kt")
    log.info("Запуск приложения")
    startKoinModules()

    val courses: UpdateCourses = get(UpdateCourses::class.java)
    val cross: CrossCourses = get(CrossCourses::class.java)
    val formulaMgr: FormulaManager = get(FormulaManager::class.java)
    val banManager: CookieBanManager = get(CookieBanManager::class.java)
    val verifyLinkManager: VerifyLinkManager = get(VerifyLinkManager::class.java)
    val tempOrderStorage: TempOrderStorage = get(TempOrderStorage::class.java)
    val scope = CoroutineScope(Dispatchers.IO)
    val payin: PayInAPI = get(Bybit::class.java)
    val payout: PayOutAPI = get(Bybit::class.java)
    val autopay: Autopay = get(Autopay::class.java)
    val tg: TelegramBot = get(TelegramBot::class.java)
    val referralManager: ReferralManager = get(ReferralManager::class.java)

    scope.launch {
        while(true) {
            try {
                banManager.checkCookieName()
                banManager.checkOldBans()
                verifyLinkManager.cleanOldLinks()
                tempOrderStorage.refresh()

                // Запросим последние цены с бирж
                val jobs = courses.update()
                jobs.joinAll()

                // Обновим граф
                cross.update()

                // Обновим цену для формул или пересчитаем их
                log.info("Пересчитываю цены для формул..")
                formulaMgr.updatePrice()
                log.info("Курсы обновлены")

                autopay.check()
                referralManager.deleteOldSessions()
            }
            catch(exception: Exception) {
                log.error(exception.message ?: "Неизвестная ошибка")
            }
            delay(60 * 1000)
        }
    }
    tg.startBot()

    if(COOKIE_PRODUCTION) {
        embeddedServer(Netty, applicationEnvironment { log }, {
            envConfig()
        }, module = Application::module).start(wait = true)
    }
    else {
        embeddedServer(Netty, host = optFromFile.host, port = optFromFile.port, module = Application::module).start(wait = true)
    }
}

private fun ApplicationEngine.Configuration.envConfig() {
    val log = LoggerFactory.getLogger("Application.envConfig")
    val opt = Options()
    val ks = KeyStore.getInstance("JKS")
    val homeDir = System.getProperty("user.home")
    val keyStoreFile = File("$homeDir/keystore.jks")
    ks.load(FileInputStream(keyStoreFile), opt.keyStorePassword.toCharArray())

    connector {
        port = if(COOKIE_PRODUCTION) 80 else FRONT_PORT_DEV
//        log.info("HTTP connector bound to ${host}:${port}")
    }
    sslConnector(
        keyStore = ks,
        keyAlias = opt.keyStoreAlias,
        keyStorePassword = { opt.keyStorePassword.toCharArray() },
        privateKeyPassword = { opt.keyStorePassword.toCharArray() }) {
        port = opt.port
        keyStorePath = keyStoreFile
//        log.info("HTTPS connector bound to ${host}:${port}")
    }
}

fun Application.module() {
    val log = LoggerFactory.getLogger("Application.module")
    val optionsMgr: OptionsManager = get(OptionsManager::class.java)
    val opt = optionsMgr.getOptions()
    val optFromFile = Options()

    install(RateLimit) {
        // По умолчанию
        register {
            rateLimiter(limit = opt.maxRequests, refillPeriod = 60.seconds)
            requestKey { call ->
                call.getRealIP()
            }
        }

        // Важные маршруты: создание заявки, вход на сайт
        register(RateLimitName("serious")) {
            rateLimiter(limit = opt.maxRequestsSerious, refillPeriod = 60.seconds)
            requestKey { call ->
                call.getRealIP()
            }
        }
    }

    install(Compression) {
        gzip {
            priority = 1.0
        }
        minimumSize(1024) // Compress responses >= 1KB
        matchContentType(
            ContentType.Text.Any,
            ContentType.Application.JavaScript,
            ContentType.Application.Json
        )
        condition {
            request.headers[HttpHeaders.Referrer]?.startsWith(optFromFile.host) == true
        }
    }

    if(COOKIE_PRODUCTION) {
        install(HttpsRedirect) {
            permanentRedirect = true
            // Handle Cloudflare proxy
            exclude { call ->
                call.request.header("X-Forwarded-Proto") == "https"
            }
        }
    }

    install(CookieProtection)

    //подключаемся к базе сразу же
    val access: AccessControl = get(AccessControl::class.java)
    access.fakeMethod()

    install(ContentNegotiation) {
        json()
        xml()
    }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true
        if(COOKIE_PRODUCTION) {
            allowHost(optFromFile.host, schemes = listOf("https"))
        }
        else anyHost()
    }

    routing {
        rateLimit(RateLimitName("serious")) {
            login()
            putOrder()
            verify()
            courseXml()
        }

        rateLimit {
            // проверка сессии
            get("/checkSession") {
                log.debug("checking session..")
                val sessionManager: SessionManager = get(SessionManager::class.java)
                val token = call.request.cookies[COOKIE_AUTH_TOKEN]
                if (token == null || !sessionManager.validateSession(token)) {
                    log.debug("no session found")
                    call.respondApi<Unit>(
                        response = ApiResponse.Error(
                            message = "Сессия не найдена или срок действия истек",
                            action = ACTION.LOGIN
                        ),
                        statusCode = HttpStatusCode.Forbidden
                    )
                    return@get
                } else {
                    log.debug("session ok")
                    call.respondApi<Unit>(
                        ApiResponse.Success(message = "Все отлично! Сессия действительна.")
                    )

//                    call.respond(mapOf("isMaintenance" to opt.isMaintenance), typeInfo<Map<String, Boolean>>())
                }
            }

            post("/generateRefCookie") {
                log.debug("generating ref cookie..")
                val referralManager: ReferralManager = get(ReferralManager::class.java)
                val body = call.receive<Map<String, JsonElement>>().trimmed()
                val refId = body["rid"]?.jsonPrimitive?.content?.toIntOrNull() ?: throw ValidationError("Не передан rid реферала")
                val refUUID = referralManager.generateRefCookie(refId)
                call.response.cookies.append(
                    Cookie(
                        name = COOKIE_REF_NAME,
                        value = refUUID,
                        httpOnly = true,
                        secure = COOKIE_PRODUCTION,
                        path = "/",
                        maxAge = 60 * 60 * 24 * 7, //7 дней
                        domain = if(COOKIE_PRODUCTION) optFromFile.host else null,
                        extensions = mapOf("SameSite" to "strict")
                    )
                )
                call.respondApi<Unit>(ApiResponse.Success(message = "OK"))
            }

            googleAnalytics()

            route("/api") {
                install(AuthPlugin)
                news()
                options()
                logout()
                users()
                roles()
                currencies()
                currencyFields()
                exchanges()
                directions()
                formulas()
                orders()
                notify()
                autoPay()
                reviews()
                referrals()
                cashback()
            }

            // Serve static files (React frontend)
            staticResources("/", "react") {
                default("index.html")
            }
        }
    }

    install(StatusPages) {
        //для ошибок входа отправим текст, понятный пользователю
        exception<Throwable> { call, cause ->
            if(cause is AttackError) {
                call.respondApi<Unit>(
                    ApiResponse.Error(message = cause.message ?: "Доступ запрещен"),
                    HttpStatusCode.Forbidden
                )
            }
            else if(cause is LoginError) {
                call.respondApi<Unit>(
                    ApiResponse.Error(message = cause.message ?: "Ошибка аутентификации"),
                    HttpStatusCode.Unauthorized
                )
            }
            else if(cause is AuthError) {
                call.respondApi<Unit>(
                    ApiResponse.Error(message = cause.message ?: "Недостаточно прав"),
                    HttpStatusCode.Forbidden
                )
            }
            else if(cause is ValidationError) {
                call.respondApi<Unit>(
                    ApiResponse.Error(message = cause.message ?: "Недопустимое значение в одном из полей"),
                    HttpStatusCode.BadRequest
                )
            }
        }

        status(HttpStatusCode.Unauthorized) { call, status ->
            val msg = "Ошибка 401: Требуется аутентификация"
            log.warn("${call.getRealIP()}: ${msg} для ${call.request.uri}")
            call.respondApi<Unit>(
                ApiResponse.Error(message = msg),
                status
            )
        }
        status(HttpStatusCode.Forbidden) { call, status ->
            val msg = "Ошибка 403: Недостаточно прав"
            log.warn("${call.getRealIP()}: ${msg} для ${call.request.uri}")
            call.respondApi<Unit>(
                ApiResponse.Error(message = msg),
                status
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            val msg = "Ошибка 404: Страница не найдена"
            log.warn("${call.getRealIP()}: Ошибка 404: Страница не найдена: ${call.request.uri}")
            call.respondApi<Unit>(
                ApiResponse.Error(message = msg),
                status
            )
        }
        status(HttpStatusCode.InternalServerError) { call, status ->
            val msg = "Код 500: Внутренняя ошибка сервера"
            log.warn("${call.getRealIP()}: Ошибка 500: ${call.request.uri}, ${status.description}")
            call.respondApi<Unit>(
                ApiResponse.Error(message = msg),
                status
            )
        }
    }
}

// При каждом запросе от пользов обновляем сессию
val AuthPlugin = createRouteScopedPlugin("AuthPlugin") {
    onCall { call ->
        val log = LoggerFactory.getLogger("AuthPlugin")
        val sessionManager: SessionManager = get(SessionManager::class.java)
        val cookieGen: CookieGen = get(CookieGen::class.java)
        val accessControl: AccessControl = get(AccessControl::class.java)

        // разрешаем доступ к некоторым точкам для всех
        val uriPath = call.request.path()
        val greenGetList = setOf("^/api/currencyFields$", "^/api/currency/\\d+/fields$", "^/api/currencies$",
            "^/api/currency/\\d+/validate/.*$", "^/api/directions$", "^/api/findDirectionId$",
            "^/api/user/direction/\\d+$", "^/api/news(/\\d+)?$",
            "^/api/(user/)?reserve(s|/.*)?$", "^/api/orderStatus/\\d+$",
            "^/api/publicReviews(/\\d+)?$", "^/api/maintenance$" )
        val greenPutList = setOf("^/api/user/order$")

        val strictList = setOf("/api/formulas/generate") // запросы, которые запрещены даже с GET
        if(greenGetList.any { it.toRegex().matches(uriPath) && call.request.httpMethod == HttpMethod.Get}) {
            return@onCall
        }
        if(greenPutList.any { it.toRegex().matches(uriPath) && call.request.httpMethod == HttpMethod.Put}) return@onCall

        val token = call.request.cookies[COOKIE_AUTH_TOKEN]
        if(token == null) {
            log.withUser(call,"Попытка доступа без cookie и без токена")
            call.respondApi<Unit>(ApiResponse.Error("Для запроса необходима авторизация"), HttpStatusCode.Unauthorized)
            return@onCall
        }

        if(!sessionManager.validateSession(token)) {
            cookieGen.delete(call)
            log.withUser(call, "Сессия истекла.")
            call.respondApi<Unit>(ApiResponse.Error("Сессия истекла. Войдите заново"), HttpStatusCode.Unauthorized)
            return@onCall
        }

        // проверить права доступа к странице
        runCatching {
            // проверяем права или даем зеленый свет всем
            // TODO: включить красный
            val requiredPermissions = routePermissions.entries.firstOrNull {
                uriPath.matches(it.key.toRegex())
            }?.value ?: emptyList()

            val userId =
                sessionManager.getUserIdByToken(token) ?: throw LoginError("Доступ запрещен: сессия не найдена")
            val user = accessControl.getUserById(userId)
                ?: throw LoginError("Доступ запрещен: пользователь с id ${userId} не найден")
            val role = accessControl.getRoleById(user.roleId)
                ?: throw LoginError("Доступ запрещен: роль с id ${user.roleId} не найдена")
            if (requiredPermissions.any { permission -> role[permission] as? Boolean != true
                && call.request.httpMethod != HttpMethod.Get }) {
                throw AuthError("Доступ запрещен: Нужны права: ${requiredPermissions.joinToString()}")
            }
        }.getOrElse {
            if(it is LoginError) {
                val logText = it.logInfo ?: it.message ?: "Неизвестная ошибка"
                log.withUser(call, logText, LOG_TYPE.WARN)
            }
            if(it is AuthError) {
                val logText = it.logInfo ?: it.message ?: "Неизвестная ошибка"
                log.withUser(call, logText, LOG_TYPE.WARN)
            }
            throw it // т.к. код Unauthorized/Forbidden будет перехвачен в Ktor, отправим исключение ему
        }

        cookieGen.generate(call, token)
    }
}


fun Logger.withUser(call: ApplicationCall, message: String, loggerType: LOG_TYPE = LOG_TYPE.INFO) {
    val remoteAddress = call.getRealIP()
    val outList = mutableListOf(remoteAddress)

    outList.add("user: ${call.getUsername()}")

    outList.add(message)
    val out = outList.joinToString(", ")
    when(loggerType) {
        LOG_TYPE.INFO -> info(out)
        LOG_TYPE.WARN -> warn(out)
        LOG_TYPE.DEBUG -> debug(out)
        LOG_TYPE.ERROR -> error(out)
    }
}

enum class LOG_TYPE { INFO, WARN, ERROR, DEBUG }

// Возьмем смещение в минутах и создадим строку timezone ("GMT-04:00")
fun getTimeZoneIDFromOffset(offsetMinutes: Int): String {
    val hours = offsetMinutes / 60
    val minutes = Math.abs(offsetMinutes % 60)

    val sign = if(offsetMinutes >= 0) "+" else "-"
    return String.format("GMT%s%02d:%02d", sign, Math.abs(hours), minutes)
}

class ValidationError(message: String) : Exception(message)
class AttackError(message: String) : Exception(message)
class AuthError(message: String, val logInfo: String? = null) : Exception(message)

//ограничиваем только POST, PUT, DELETE.
//GET оставляем открытым.
val routePermissions = mapOf(
    "^/api/countNews$" to listOf("isEditNews"),
    "^/api/news(/(\\d+)|\\?.*)?$" to listOf("isEditNews"),

    "^/api/options$" to listOf("isEditOptions"),

    "^/api/users(/(\\d+)|\\?.*)?$" to listOf("isEditUserAndRole"), // /api/users, /api/users/2, /api/users?params
    "^/api/countUsers$" to listOf("isEditUserAndRole"), //TODO: ограничить GET
    "^/api/roles(\\?ids=\\d+(,\\d+)*)?$" to listOf("isEditUserAndRole"), //api/roles или api/roles?ids=1,2

    "^/api/currency$" to listOf("isEditCurrency"),
    "^/api/currencies(\\?ids=\\d+(,\\d+)*)?$" to listOf("isEditCurrency"),
    "^/api/currency/\\d+/fields$" to listOf("isEditCurrency"),
    "^/api/currencyField(s)?(\\?ids=\\d+(,\\d+)*)?$" to listOf("isEditCurrency"),
    "^/api/currencyField/\\d+/currencies$" to listOf("isEditCurrency"),
    "^/api/exchanges/.*$" to listOf("isEditCurrency"),
    "^/api/courses/.*$" to listOf("isEditCurrency"),
    "^/api/payin/.*$" to listOf("isEditCurrency"),
    "^/api/payout/.*$" to listOf("isEditCurrency"),
    "^/api/reserve/.*$" to listOf("isEditReserve"),
    "^/api/direction.*$" to listOf("isEditDirection"),
    "^/api/formula.*$" to listOf("isEditDirection"),
    "^/api/orderStatus.*$" to listOf("isEditDirection"),
    "^/api/order(/\\d+)$" to listOf("isEditOrder"),
    "^/api/notify/users$" to listOf("isEditNotify"),
    "^/api/notify/admin$" to listOf("isEditNotify"),
    "^/api/reviews$" to listOf("isEditReview"),
    "^/api/referrals_paid$" to listOf("isSendReferralPayouts"),

    //все пользов, которые могут зайти с паролем, могут откр эти URL.
    //isWebsiteUser проверять не нужно.
//    "^/api/user/orders" to listOf("isWebsiteUser"),
//    "^/api/user/order(/\\d+)$" to listOf("isWebsiteUser"),
//    "^/api/user/order/claim$" to listOf("isWebsiteUser")

    // Эти маршруты не проверяются, т.к имеют тип GET,
    // но для правил безопасности возможно, они пригодятся
    //    "^/api/currencies/getValidators$" to listOf("isEditCurrency"),
    // /currency/{id}/validate/{address}
)

