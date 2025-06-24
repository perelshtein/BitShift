package com.github.perelshtein.exchanges

import com.github.perelshtein.Hashing
import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.Exchange
import com.github.perelshtein.database.ExchangeManager
import com.github.perelshtein.roundToStep
import com.github.perelshtein.roundup
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Collections
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.filter
import kotlin.collections.firstOrNull
import kotlin.getValue

class Mexc: KoinComponent, ExchangeAPI, BalanceAPI, PayInAPI, PayOutAPI {
    private val mgr: ExchangeManager by inject()
    private val client: HttpClient by inject()
    private val MEXC = Mexc::class.simpleName!!
    private val log = LoggerFactory.getLogger("Mexc")
    private var exchangeInfo: Exchange
    private val payinPairs = Collections.synchronizedSortedMap(TreeMap<String, PayCurrency>())
    private val payoutPairs = Collections.synchronizedSortedMap(TreeMap<String, PayCurrency>())
    private var lastPayinUpdate: LocalDateTime = LocalDateTime.now().minusDays(1L)
    private var lastPayoutUpdate: LocalDateTime = LocalDateTime.now().minusDays(1L)
    private val json = Json {
         ignoreUnknownKeys = true
    }
    private val mutex = Mutex()
    private val payinMap = ConcurrentHashMap<String, PaymentInfo>()
    private val payoutMap = ConcurrentHashMap<String, PaymentInfo>()
    private val tradeLimits = ConcurrentHashMap<String, CachedTradeLimit>()
    private val feeRates = ConcurrentHashMap<String, CachedFeeRate>()

    init {
        if(mgr.getExchangeByName(MEXC) == null) {
            mgr.addExchange(Exchange {
                name = Mexc::class.simpleName!!
                updatePeriod = 1
                maxFailCount = 5
                isEnabled = true
                url = "https://api.mexc.com"
                lastUpdate = LocalDateTime.now().minusMinutes(2L)
            })
        }
        exchangeInfo = mgr.getExchangeByName(MEXC)!!
    }


    private suspend fun get(path: String, isSecure: Boolean = false, paramsMap: Map<String, Any>? = null): JsonElement {
        var headersMap: Map<String, Any>? = null

        val map = mutableMapOf<String, Any>()
        paramsMap?.forEach { map.put(it.key, it.value.toString().encodeURLParameter()) }

        if(isSecure) {
            map["timestamp"] = Instant.now().toEpochMilli()
            val apiKey = mgr.getApiKeys(exchangeInfo.id)?.apiKey ?: ""
            val secretKey = mgr.getApiKeys(exchangeInfo.id)?.secretKey ?: ""
            if(apiKey.isEmpty() || secretKey.isEmpty()) {
                throw ValidationError("Mexc: Не указан API-ключ или секретный ключ.")
            }
            val hashing: Hashing by inject { parametersOf(secretKey) }
            val hash = hashing.encode(map.entries.joinToString("&") { "${it.key}=${it.value}" }).getOrThrow()
            map["signature"] = hash
            headersMap = mapOf(
                "X-MEXC-APIKEY" to apiKey
            )
        }

        var url = exchangeInfo.url.trim('/') + "/" + path.trimStart('/')
        if(map.isNotEmpty()) url += "?" + map.entries.joinToString("&") { "${it.key}=${it.value}" }

        val response = client.get(url) {
            headersMap?.forEach {
                headers.append(it.key, "${it.value}")
            }
        }
        if(response.status == HttpStatusCode.TooManyRequests) throw Exception("превышен лимит запросов")
        if(response.status != HttpStatusCode.OK) {
            val details = response.body<MexcError>()
            throw Exception("HTTP-код ${response.status}.\n" +
                    "Ответ сервера: ${details.code}: ${details.msg}")
        }
        return response.body<JsonElement>()
    }

    private suspend fun post(path: String, isSecure: Boolean = false, paramsMap: Map<String, Any>): JsonElement {
        exchangeInfo = mgr.getExchangeByName(MEXC)!! //если поменялся url в настройках, учтем это
        var headersMap: Map<String, Any>? = null
        val map = mutableMapOf<String, Any>()
        paramsMap.forEach { map.put(it.key, it.value.toString().encodeURLParameter()) }

        if(isSecure) {
            map["timestamp"] = Instant.now().toEpochMilli().toString()
            val apiKey = mgr.getApiKeys(exchangeInfo.id)?.apiKey ?: ""
            val secretKey = mgr.getApiKeys(exchangeInfo.id)?.secretKey ?: ""
            if(apiKey.isEmpty() || secretKey.isEmpty()) {
                throw ValidationError("Mexc: Не указан API-ключ или секретный ключ.")
            }
            val hashing: Hashing by inject { parametersOf(secretKey) }
            val hash = hashing.encode(map.entries.joinToString("&") {
                "${it.key}=${it.value}"
            }).getOrThrow()
            map["signature"] = hash
            headersMap = mapOf(
                "X-MEXC-APIKEY" to apiKey
            )
        }

        var url = exchangeInfo.url.trim('/') + "/" + path.trimStart('/')
        if(map.isNotEmpty()) url += "?" + map.entries.joinToString("&") { "${it.key}=${it.value}" }

        log.info(url)
        val response = client.post(url) {
            headersMap?.forEach {
                headers.append(it.key, it.value.toString())
            }
        }
        if(response.status == HttpStatusCode.TooManyRequests) throw Exception("превышен лимит запросов")
        if(response.status != HttpStatusCode.OK) {
            val details = response.body<MexcError>()
            throw Exception("HTTP-код ${response.status}.\n" +
                    "Ответ сервера: ${details.code}: ${details.msg}")
        }
        return response.body<JsonElement>()
    }

    override suspend fun fetchCodes(): Set<String> {
        runCatching {
            log.debug("Запрос списка торговых пар...")
            val result = get("/api/v3/exchangeInfo")
            val answer = json.decodeFromJsonElement<MexcPayCurrencies>(result)
            return answer.symbols
                .filter { it.permissions.contains("SPOT") && it.orderTypes.contains("MARKET") }
                .flatMap {
                    listOf(it.baseAsset, it.quoteAsset)
                }.toSet()
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось получить список пар.\n${msg}")
            throw exception
        }
    }

    override suspend fun fetchCourses(): List<Course> {
        runCatching {
            log.debug("Загрузка курсов...")
            val result = get("/api/v3/ticker/bookTicker")
            val answer = json.decodeFromJsonElement<List<MexcBookTicker>>(result)

            return answer.map {
                Course(
                    from = it.symbol,
                    to = "",
                    buy = it.bidPrice?.toDoubleOrNull() ?: 0.0,
                    sell = it.askPrice?.toDoubleOrNull() ?: 0.0
                )
            }.filter { it.buy > 0.0 && it.sell > 0.0 }
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось получить курсы.\n${msg}")
            throw exception
        }
    }

    override suspend fun fetchBalance(): Double {
        runCatching {
            log.debug("Запрос баланса...")
            var result = get("/api/v3/account", true)
            val answer = json.decodeFromJsonElement<MexcBalance>(result)
            log.debug("Баланс загружен успешно.")
            return answer.balances.firstOrNull { it.asset == "USDT" }?.free?.toDouble() ?: 0.0
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось загрузить баланс.\n${msg}")
            throw exception
        }
    }

    override suspend fun receive(quantity: Double, currency: String, chain: String?): PaymentInfo {
        runCatching {
            log.debug("Создание входящего перевода..")
            val currencyObj = getCurrenciesPayin().get(currency)
            if (currencyObj == null) throw ValidationError("Валюта ${currency} не поддерживается для входящих переводов")
            val chainObj = if(chain == null) currencyObj.chains.firstOrNull() else currencyObj.chains.firstOrNull { it.code == chain }
            if (chainObj == null) throw ValidationError("Сеть ${chain} не поддерживается для входящих переводов")
            if (quantity !in chainObj.minAmount..chainObj.maxAmount) {
                throw ValidationError("Объем ${currency} должен быть в диапазоне от ${chainObj.minAmount} до ${chainObj.maxAmount}")
            }

            val params = mapOf("coin" to chainObj.currencyCode, "network" to chainObj.name)
            val result = get("/api/v3/capital/deposit/address", true, params)
            val answer = json.decodeFromJsonElement<List<MexcDeposit>>(result)
            if(answer.isEmpty()) throw ValidationError("Не удалось получить адрес для входящих переводов в сети ${chainObj.name}.")
            val chainFound = answer.firstOrNull { it.network == chainObj.name }
            if(chainFound == null) throw ValidationError("Сеть ${chainObj.name} не поддерживается для входящих переводов.")

            val now = LocalDateTime.now()
            //это фишка Mexc - разные коды валют для разных сетей
            val sameOrders = payinMap.values.filter { it.currency == chainObj.currencyCode && it.chain == chain &&
                    it.status in listOf("new", "waitingForPayment", "waitingForConfirmation") }
            val payment = PaymentInfo(
                created = now,
                updated = now,
                wallet = chainFound.address,
                currency = chainObj.currencyCode,
                chain = chainObj.name,
                quantity = quantity,
                uuid = UUID.randomUUID().toString(),
                status = "new",
                needsTxId = sameOrders.size > 0
            )
            payinMap[payment.uuid] = payment
            if(payment.needsTxId) {
                sameOrders.forEach {
                    it.needsTxId = true
                }
            }
            log.info("Входящий перевод на сумму ${quantity} ${currency} в сети ${chainObj.code} создан.")
            return payment

        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось создать входящий перевод.\n${msg}")
            throw exception
        }
    }

    override suspend fun checkPayin(uuid: String): PaymentInfo {
        runCatching {
            payinMap[uuid]?.let { payment ->
                if(payment.updated.plusSeconds(10L).isAfter(LocalDateTime.now()) || payment.status == "completed" ||
                    payment.status == "error") return payment
                val params = mutableMapOf(
                    // не работает
//                    "coin" to payment.currency,
                    "startTime" to payment.created.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )

                val result = get("/api/v3/capital/deposit/hisrec", true, params)
                 val answer = json.decodeFromJsonElement<List<MexcDepositStatus>>(result)

                val chainObj = if(payment.needsTxId) {
                    if(payment.txid != null) answer.firstOrNull { it.txId == payment.txid && it.amount >= payment.quantity }
                    else null
                }
                else {
                    answer.firstOrNull { it.coin == payment.currency && it.network == payment.chain &&
                            it.amount >= payment.quantity }
                }
                if(chainObj == null) {
                    log.info("Ждем перевода на сумму >= ${payment.quantity} ${payment.currency} в сети ${payment.chain}...")
                    payment.status = "waitingForPayment"
                    return payment
                }

                payment.status = when(chainObj.status) {
                    4 -> {
                        log.info("Ждем подтверждения перевода: ${chainObj.confirmTimes} / ${chainObj.unlockConfirm}, сумма >= ${payment.quantity} ${payment.currency}, сеть ${payment.chain}...")
                        "waitingForConfirmation"
                    }
                    9 -> {
                        log.info("Ждем подтверждения перевода: ${chainObj.confirmTimes} / ${chainObj.unlockConfirm}, сумма >= ${payment.quantity} ${payment.currency}, сеть ${payment.chain}...")
                        "waitingForConfirmation"
                    }
                    5 -> "payed"
                    7 -> "error"
                    else -> "waitingForPayment"
                }
                payment.confirmations = chainObj.confirmTimes
                payment.tag = chainObj.memo
                payment.updated = LocalDateTime.now()
                return payment
            }
            throw ValidationError("Входящий перевод не найден")
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось проверить статус входящего перевода.\n${msg}")
            throw exception
        }
    }

    override suspend fun getCurrenciesPayin(): Map<String, PayCurrency> {
        if(payinPairs.isNotEmpty() && lastPayinUpdate.plusDays(1L).isAfter(LocalDateTime.now())) {
            return payinPairs
        }
        runCatching {
            log.debug("Запрос списка валют для приема переводов...")
            val result = get("/api/v3/exchangeInfo")
            val answer = json.decodeFromJsonElement<MexcPayCurrencies>(result)

            val resultDetail = get("/api/v3/capital/config/getall", true)
            val answerDetail = json.decodeFromJsonElement<List<MexcDetail>>(resultDetail)
            val tempMap = answer.symbols
                .filter { it.permissions.contains("SPOT") && it.orderTypes.contains("MARKET")
                        && it.quoteAsset == "USDT" }
                .associate { code ->
                    code.baseAsset to answerDetail.firstOrNull { it.coin == code.baseAsset }?.let { row ->
                    PayCurrency(
                        name = row.name,
                        code = row.coin,
                        chains = row.networkList
                            .filter { it.depositEnable }
                            .map {
                                PayCurrency.Chain(
                                    name = it.network,
                                    code = it.network,
                                    advancedCode = it.netWork,
                                    currencyCode = it.name,
                                    confirmation = it.minConfirm,
                                    fidelity = Math.pow(10.0, -1 * code.baseAssetPrecision.toDouble()),
                                    minAmount = code.baseSizePrecision.toDoubleOrNull() ?: 0.0,
                                    maxAmount = Double.MAX_VALUE,
                                    fixedFee = 0.0,
                                    percentageFee = 0.0
                                )
                            }
                    )
                }
            }.filterValues { it != null }.toSortedMap()

//            var usdtChainsTemp = setOf<PayCurrency.Chain>()
            answerDetail.firstOrNull { it.coin == "USDT" }?.let { row ->
                tempMap["USDT"] = PayCurrency(
                    name = row.name,
                    code = row.coin,
                    chains = row.networkList
                        .filter { it.depositEnable }
                        .map {
                            PayCurrency.Chain(
                                name = it.network,
                                code = it.network,
                                currencyCode = it.name,
                                confirmation = it.minConfirm,
                                fidelity = 0.01,
                                minAmount = 0.0,
                                maxAmount = 100000.0,
                                fixedFee = 0.0,
                                percentageFee = 0.0
                            )
                        }
                )
            }

            mutex.withLock {
                payinPairs.clear()
                payinPairs.putAll(tempMap)
                lastPayinUpdate = LocalDateTime.now()
//                usdtChains = usdtChainsTemp
            }
            return tempMap as Map<String, PayCurrency>
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось загрузить список валют для приема переводов.\n${msg}")
            throw exception
        }
    }

    override suspend fun getCurrenciesPayout(): Map<String, PayCurrency> {
        if(payoutPairs.isNotEmpty() && lastPayoutUpdate.plusDays(1L).isAfter(LocalDateTime.now())) {
            return payoutPairs
        }
        runCatching {
            log.debug("Запрос списка валют для отправки переводов...")
            val result = get("/api/v3/exchangeInfo")
            val answer = json.decodeFromJsonElement<MexcPayCurrencies>(result)
//            answer.symbols.filter { it.baseAsset.uppercase().contains(Regex("BTC")) }.forEach { code ->
//                log.info(code.baseAsset + " " + code.quoteAsset + " " + code.isSpotTradingAllowed + " " + code.permissions + " " + code.orderTypes)
//            }

            val resultDetail = get("/api/v3/capital/config/getall", true)
            val answerDetail = json.decodeFromJsonElement<List<MexcDetail>>(resultDetail)
            val tempMap = answer.symbols
                .filter { it.permissions.contains("SPOT") && it.orderTypes.contains("MARKET") // it.isSpotTradingAllowed &&
                        && it.quoteAsset == "USDT" }
                .associate { code ->
//                    if(code.baseAsset.uppercase().contains(Regex("BTC|BITCOIN|ETH|ETHER"))) {
//                        log.info(code.baseAsset)
//                    }
                    code.baseAsset to answerDetail.firstOrNull { it.coin == code.baseAsset }?.let { row ->
                        PayCurrency(
                            name = row.name,
                            code = row.coin,
                            chains = row.networkList
                                .filter { it.withdrawEnable }
                                .map {
                                    PayCurrency.Chain(
                                        name = it.network,
                                        code = it.network,
                                        advancedCode = it.netWork,
                                        currencyCode = it.name,
                                        confirmation = it.minConfirm,
                                        fidelity = Math.pow(10.0, -1 * code.baseAssetPrecision.toDouble()),
                                        minAmount = it.withdrawMin.toDoubleOrNull() ?: 0.0,
                                        maxAmount = it.withdrawMax.toDoubleOrNull() ?: Double.MAX_VALUE,
                                        fixedFee = it.withdrawFee.toDoubleOrNull() ?: 0.0,
                                        percentageFee = 0.0
                                    )
                                }
                        )
                    }
                }.filterValues { it != null }.toSortedMap()

            answerDetail.firstOrNull { it.coin == "USDT" }?.let { row ->
                tempMap["USDT"] = PayCurrency(
                    name = row.coin,
                    code = row.coin,
                    chains = row.networkList
                        .filter { it.depositEnable }
                        .map {
                            PayCurrency.Chain(
                                name = it.network,
                                code = it.network,
                                advancedCode = it.netWork,
                                currencyCode = it.name,
                                confirmation = it.minConfirm,
                                fidelity = 0.01,
                                minAmount = it.withdrawMin.toDoubleOrNull() ?: 0.0,
                                maxAmount = it.withdrawMax.toDoubleOrNull() ?: 100000.0,
                                fixedFee = it.withdrawFee.toDoubleOrNull() ?: 0.0,
                                percentageFee = 0.0
                            )
                        }
                )
            }

            mutex.withLock {
                payoutPairs.clear()
                payoutPairs.putAll(tempMap)
                lastPayoutUpdate = LocalDateTime.now()
            }
            return tempMap as Map<String, PayCurrency>
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось загрузить список валют для отправки переводов.\n${msg}")
            throw exception
        }
    }

    override suspend fun createInternalTransfer(coin: String, amount: Double, fromAccount: String, toAccount: String) {
        //no implementation needed - Mexc have one account only
    }

    override suspend fun swap(from: String, to: String, side: String, amountFrom: Double?, amountTo: Double?): SwapResult {
        val map = TreeMap<String, Any>()
        map["symbol"] = "$from$to"
        map["side"] = side.uppercase()
        map["type"] = "MARKET"
        val limits = getTradeLimits(from, to)
        if(amountFrom != null) {
            val amountRounded = amountFrom.roundToStep(limits.baseStep)
            map["quantity"] = amountRounded
            log.info("Меняем: $side $amountRounded $from за $to")
        }
        else if(amountTo != null) {
            val amountRounded = amountTo.roundToStep(limits.quoteStep)
            map["quoteOrderQty"] = amountRounded
            log.info("Меняем: $side $amountRounded $to за $from")
        }
        else throw ValidationError("Не указана сумма для конвертации $from -> $to")
        val answer = post("/api/v3/order", true, map)
        val id = json.decodeFromJsonElement<MexcCreateOrder>(answer).orderId

        //ждем обработки ордера
        var attempts = 0
        val maxAttempts = 6 //30 сек
        while (attempts < maxAttempts) {
            delay(5000L)
            val answer = get("/api/v3/order", true, mapOf("symbol" to "$from$to", "orderId" to id))
            val result = json.decodeFromJsonElement<MexcOrderStatus>(answer)
            if(result.orderId != id) throw ValidationError("Ордер не найден для $from -> $to")
            if(result.status == "CANCELED") throw ValidationError("Ордер отклонен для $from -> $to")

            if(result.status == "FILLED") {
                log.debug("Конвертация $from -> $to по курсу $result.price выполнена")
                if(result.executedQty == 0.0) throw ValidationError("Объем ордера неизвестен для $from -> $to")
                return SwapResult(result.price, result.executedQty, 0.0)
            }
            attempts++
        }
        throw ValidationError("Конвертация $from -> $to не завершена за 30 секунд")
    }

    override suspend fun getTradeLimits(from: String, to: String): TradeLimits {
        runCatching {
            log.debug("Запрос лимитов для торгов на Spot...")
            tradeLimits["$from$to"]?.let {
                if(it.lastUpdated.plusMinutes(1L).isAfter(LocalDateTime.now())) {
                    return it.limit
                }
            }

            val pair = "$from$to".uppercase()
            val result = get("/api/v3/exchangeInfo", paramsMap = mapOf("symbol" to pair))
            val answer = json.decodeFromJsonElement<MexcPayCurrencies>(result).symbols
                .firstOrNull {
                    it.baseAsset == from.uppercase() && it.quoteAsset == to.uppercase() }?.let {
                TradeLimits(minValue = it.quoteAmountPrecisionMarket.toDoubleOrNull() ?: 0.0,
                    maxValue = it.maxQuoteAmountMarket.toDoubleOrNull() ?: Double.MAX_VALUE,
                    minQty = it.baseSizePrecision.toDoubleOrNull() ?: 0.0,
                    maxQty = Double.MAX_VALUE,
                    baseStep = Math.pow(10.0, -1 * it.baseAssetPrecision.toDouble()),
                    quoteStep = Math.pow(10.0, -1 * it.quoteAssetPrecision.toDouble())
                )
            }
            if(answer != null) {
                mutex.withLock {
                    tradeLimits["$from$to"] = CachedTradeLimit(LocalDateTime.now(), answer)
                }
                return answer
            }
            else throw ValidationError("Лимиты для $from -> $to неизвестны")
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось проверить лимиты для торгов на Spot.\n${msg}")
            throw exception
        }
    }

    override suspend fun saveTxId(uuid: String, txid: String) {
        payinMap[uuid]?.let {
            if(it.status != "completed" && it.status != "error" && it.status != "cancelled") it.txid = txid
            else throw ValidationError("Невозможно сохранить txid - входящий перевод завершен")
            return
        }
        throw ValidationError("Невозможно сохранить txid - входящий перевод не найден")
    }

    override suspend fun send(wallet: String, quantity: Double, currency: String, chain: String?, tag: String?):
        PaymentInfo {
        runCatching {
            log.debug("Создание исходящего перевода..")
            val currencyObj = getCurrenciesPayout().get(currency)
            if (currencyObj == null) throw ValidationError("Валюта ${currency} не поддерживается для исходящих переводов")
            val chainObj = if(chain == null) currencyObj.chains.firstOrNull() else currencyObj.chains.firstOrNull { it.code == chain }
            if (chainObj == null) throw ValidationError("Сеть ${chain} не поддерживается для исходящих переводов")
            val grossQuantity = quantity + chainObj.fixedFee
            if (grossQuantity !in chainObj.minAmount..chainObj.maxAmount) {
                throw ValidationError("Объем ${currency} должен быть в диапазоне от ${chainObj.minAmount} до ${chainObj.maxAmount}")
            }

            // Создадим исходящий перевод
            val id = UUID.randomUUID().toString()
            val map = mutableMapOf("coin" to chainObj.currencyCode, "address" to wallet, "amount" to grossQuantity,
                "netWork" to chainObj.advancedCode, // USDT FAIL!!!
                "withdrawOrderId" to id.substring(0,32))
            val now = LocalDateTime.now()
            post("/api/v3/capital/withdraw", true, map)

            val payment = PaymentInfo(
                created = now,
                updated = now,
                wallet = wallet,
                currency = currency,
                chain = chainObj.code,
                quantity = grossQuantity,
                uuid = id,
                status = "new"
            )
            payoutMap[payment.uuid] = payment
            log.info("Исходящий перевод на сумму ${quantity} ${currency} в сети ${chainObj.code} создан")
            return payment

        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось создать исходящий перевод.\n${msg}")
            throw exception
        }
    }

    override suspend fun checkPayout(uuid: String): PaymentInfo {
        runCatching {
            payoutMap[uuid]?.let { p ->
                if (p.updated.plusSeconds(10L).isAfter(LocalDateTime.now()) || p.status == "completed" ||
                    p.status == "error") return p
                // не работает из-за час пояса
//                val params = mutableMapOf(
//                    "startTime" to p.created.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
//                )
//                log.info(params.toString())
                val result = get("/api/v3/capital/withdraw/history", true)
                val answer = json.decodeFromJsonElement<List<MexcWithdrawStatus>>(result)
                val chainObj = answer.firstOrNull { it.withdrawOrderId == uuid.subSequence(0, 32) }
                if(chainObj == null) throw ValidationError("Исходящий перевод не найден")

                p.txid = chainObj.txId
                // 1:APPLY, 2:AUDITING, 3:WAIT, 4:PROCESSING, 5:WAIT_PACKAGING, 6:WAIT_CONFIRM, 7:SUCCESS,
                // 8:FAILED, 9:CANCEL, 10:MANUAL
                p.status = when(chainObj.status) {
                    1 -> "new"
                    2 -> "onCheck"
                    3 -> "waitingForPayout"
                    4 -> "waitingForPayout"
                    5 -> "waitingForPayout"
                    6 -> "waitingForPayout"
                    7 -> "completed"
                    9 -> "cancelled"
                    else -> "error"
                }
                p.fee = chainObj.transactionFee
                p.txid = chainObj.txId
                p.tag = chainObj.memo
                p.updated = LocalDateTime.now()
                return p
            }
            throw ValidationError("Входящий перевод не найден")
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось проверить статус исходящего перевода.\n${msg}")
            throw exception
        }
    }

    override suspend fun getFeeRate(from: String, to: String): Double {
        val symbol = "$from$to".uppercase()
        feeRates[symbol]?.let {
            if(it.lastUpdated.plusMinutes(1).isAfter(LocalDateTime.now())) {
                return it.fee
            }
        }
        log.debug("Загрузка комиссии для торгов $symbol")
        val result = get("/api/v3/tradeFee", true, mapOf("symbol" to symbol))
        val answer = json.decodeFromJsonElement<MexcTradeFee>(result)
        if(answer.code != 0 || answer.data == null) {
            throw Exception("Не удается загрузить комиссию для торгов $symbol\n$" +
                    "Ответ сервера: ${answer.msg}, код: ${answer.code}")
        }
        log.debug("feeRate для $symbol: ${answer.data.takerCommission}")
        mutex.withLock {
            feeRates[symbol] = CachedFeeRate( LocalDateTime.now(), answer.data.takerCommission)
        }
        return answer.data.takerCommission
    }

    override fun deletePayin(uuid: String) {
        payinMap.remove(uuid)
    }

    override fun deletePayout(uuid: String) {
        payoutMap.remove(uuid)
    }
}

@Serializable
data class MexcPayCurrencies(
    val symbols: List<Symbol>
) {
    @Serializable
    data class Symbol(
        val symbol: String,
        val status: String,
        val baseAsset: String,
        val quoteAsset: String,
        val orderTypes: List<String>, // для рыночного ордера ищем MARKET
        val isSpotTradingAllowed: Boolean,
        val takerCommission: String,
        val baseAssetPrecision: Int, // точность в валюте А
        val baseSizePrecision: String,
        val quoteAssetPrecision: Int, // точность в валюте Б
        val quoteAmountPrecisionMarket: String, //точность в валюте Б для рыночного ордера
        val quoteAmountPrecision: String, // мин.размер в валюте Б
        val maxQuoteAmountMarket: String, // макс. размер в валюте Б для рыночного ордера
        val permissions: List<String>
    )
}

@Serializable
data class MexcDetail(
    val coin: String,
    val name: String,
    val networkList: List<Network>
) {
    @Serializable
    data class Network(
        val coin: String,
        val name: String, //BTC-BSC
        val netWork: String, //BTC
        val network: String, //BEP20(BSC)
        val depositEnable: Boolean,
        val withdrawEnable: Boolean,
        val minConfirm: Int,
        val withdrawFee: String,
        val withdrawMin: String,
        val withdrawMax: String
    )
}

@Serializable
data class MexcTradeFee(
    val code: Int,
    val msg: String,
    val data: Fee? = null
) {
    @Serializable
    data class Fee(
        val takerCommission: Double
    )
}

@Serializable
data class MexcBookTicker(
    val symbol: String,
    val bidPrice: String?,
    val askPrice: String?
)

@Serializable
data class MexcTicker(
    val symbol: String,
    val price: String?
)

@Serializable
data class MexcBalance(
    val balances: List<Balance>
) {
    @Serializable
    data class Balance(
        val asset: String,
        val free: Double
    )
}

@Serializable
data class MexcDeposit(
    val address: String,
    val coin: String,
    val memo: String? = null,
    val network: String
)

@Serializable
data class MexcWithdraw(
    val id: String
)

@Serializable
data class MexcDepositStatus(
    val coin: String,
    val network: String,
    val amount: Double,
    val status: Int, // 1:SMALL, 2:TIME_DELAY, 3:LARGE_DELAY, 4:PENDING, 5:SUCCESS, 6:AUDITING, 7:REJECTED
    var txId: String,
    val confirmTimes: Int,
    val unlockConfirm: Int,
    val memo: String? = null
)

@Serializable
data class MexcWithdrawStatus(
    val id: String,
    val coin: String,
    val network: String,
    val amount: Double,
    val status: Int, // 1:APPLY, 2:AUDITING, 3:WAIT, 4:PROCESSING, 5:WAIT_PACKAGING, 6:WAIT_CONFIRM, 7:SUCCESS,
    // 8:FAILED, 9:CANCEL, 10:MANUAL
    val txId: String?,
    val memo: String? = null,
    val transactionFee: Double,
    val address: String,
    val withdrawOrderId: String? = null
)

@Serializable
data class MexcCreateOrder(
    val orderId: String
)

@Serializable
data class MexcOrderStatus(
    val orderId: String,
    val status: String,
    val price: Double,
    val executedQty: Double
)

@Serializable
    data class MexcError(
    val code: Int,
    val msg: String
)

data class CachedTradeLimit(
    val lastUpdated: LocalDateTime,
    val limit: TradeLimits
)

data class CachedFeeRate(
    val lastUpdated: LocalDateTime,
    val fee: Double
)