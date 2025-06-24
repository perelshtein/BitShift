package com.github.perelshtein.exchanges

import com.github.perelshtein.DoubleOrZeroSerializer
import com.github.perelshtein.Hashing
import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.Exchange
import com.github.perelshtein.database.ExchangeManager
import com.github.perelshtein.roundToStep
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.modules.serializersModuleOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

const val RECV_WINDOW = 5000;

class Bybit: KoinComponent, ExchangeAPI, BalanceAPI, PayOutAPI, PayInAPI {
    private val mgr: ExchangeManager by inject()
    private val client: HttpClient by inject()
    private val BYBIT = Bybit::class.simpleName!!
    private val log = LoggerFactory.getLogger(BYBIT)
    private val json = Json {
        serializersModule = serializersModuleOf(Double::class, DoubleOrZeroSerializer)
        ignoreUnknownKeys = true
    }
    private var exchangeInfo: Exchange
    private var lastPayinUpdate: LocalDateTime = LocalDateTime.now().minusDays(1L)
    private var lastPayoutUpdate: LocalDateTime = LocalDateTime.now().minusDays(1L)
    private val payinPairs = Collections.synchronizedSortedMap(TreeMap<String, PayCurrency>())
    private val payoutPairs = Collections.synchronizedSortedMap(TreeMap<String, PayCurrency>())
    private val payinMap = ConcurrentHashMap<String, PaymentInfo>()
    private val payoutMap = ConcurrentHashMap<String, WithdrawExtendedInfo>()
    private val mutex = Mutex()
    private val feeRates = ConcurrentHashMap<String, Double>()

    init {
        if(mgr.getExchangeByName(BYBIT) == null) {
            mgr.addExchange(Exchange {
                name = Bybit::class.simpleName!!
                updatePeriod = 1
                maxFailCount = 5
                isEnabled = true
                url = "https://api.bybit.com"
                lastUpdate = LocalDateTime.now().minusMinutes(2L)
            })
        }
        exchangeInfo = mgr.getExchangeByName(BYBIT)!!
    }


    private suspend fun get(path: String, isSecure: Boolean = false, paramsMap: Map<String, Any>? = null): BybitAnswer {
        exchangeInfo = mgr.getExchangeByName(BYBIT)!! //если поменялся url в настройках, учтем это
        var url = "${exchangeInfo.url}/${path}"
        var hash: String? = null
        var headersMap: Map<String, Any>? = null

        var params = paramsMap?.entries?.reduce(fun(sum, amount): String {
            return "${sum}&${amount.key}=${amount.value}"
        })?.toString()
        if(params != null) url += "?${params}"

        if(isSecure) {
            val now = Instant.now().toEpochMilli()
            val apiKey = mgr.getApiKeys(exchangeInfo.id)?.apiKey ?: ""
            val secretKey = mgr.getApiKeys(exchangeInfo.id)?.secretKey ?: ""
            if(apiKey.isEmpty() || secretKey.isEmpty()) {
                throw ValidationError("Bybit: Не указан API-ключ или секретный ключ.")
            }
            val hashing: Hashing by inject { parametersOf(secretKey) }

            headersMap = mapOf(
                "X-BAPI-API-KEY" to apiKey,
                "X-BAPI-TIMESTAMP" to now,
                "X-BAPI-RECV-WINDOW" to RECV_WINDOW
            )
            hash = if(params != null) hashing.encode("${now}${apiKey}${RECV_WINDOW}${params}").getOrThrow()
            else hashing.encode("${now}${apiKey}${RECV_WINDOW}").getOrThrow()
        }
        val response = client.get(url) {
            headersMap?.forEach {
                headers.append(it.key, it.value.toString())
            }
            hash?.let { headers.append("X-BAPI-SIGN", it) }
        }
        if(response.status != HttpStatusCode.OK) throw Exception("HTTP-код ${response.status}")
        val answer = response.body<BybitAnswer>()
        if(answer.retCode != 0) {
            throw Exception("retCode Bybit: ${answer.retCode}\n" +
                    "retMsg Bybit: ${answer.retMsg}")
        }
        return answer
    }

    private suspend fun post(path: String, isSecure: Boolean = false, paramsMap: TreeMap<String, Any>): BybitAnswer {
        exchangeInfo = mgr.getExchangeByName(BYBIT)!! //если поменялся url в настройках, учтем это
        var url = "${exchangeInfo.url}/${path}"
        var hash: String? = null
        var headersMap: Map<String, Any>? = null
        val strMap = mutableMapOf<String, String>()
        if(isSecure) {
            val now = Instant.now().toEpochMilli()
            val apiKey = mgr.getApiKeys(exchangeInfo.id)?.apiKey ?: ""
            val secretKey = mgr.getApiKeys(exchangeInfo.id)?.secretKey ?: ""
            if(apiKey.isEmpty() || secretKey.isEmpty()) {
                throw ValidationError("Не указан API-ключ или секретный ключ.")
            }
            val hashing: Hashing by inject { parametersOf(secretKey) }

            headersMap = mapOf(
                "X-BAPI-API-KEY" to apiKey,
                "X-BAPI-TIMESTAMP" to now,
                "X-BAPI-RECV-WINDOW" to RECV_WINDOW
            )

            strMap.putAll(paramsMap.map { it.key to it.value.toString() })
            val mapEncoded = json.encodeToString(strMap)
            hash = hashing.encode("${now}${apiKey}${RECV_WINDOW}${mapEncoded}").getOrThrow()
        }
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(strMap)
            headersMap?.forEach {
                headers.append(it.key, it.value.toString())
            }
            hash?.let { headers.append("X-BAPI-SIGN", it) }
        }
        if(response.status != HttpStatusCode.OK) throw Exception("HTTP-код ${response.status}")
        val answer = response.body<BybitAnswer>()
        if(answer.retCode != 0) {
            throw Exception("retCode Bybit: ${answer.retCode}\n" +
                    "retMsg Bybit: ${answer.retMsg}")
        }
        return answer
    }

    override suspend fun fetchCourses(): List<Course> {
        runCatching {
            log.debug("Загрузка курсов...")
            val result = get("/v5/market/tickers?category=spot").result
            if(result == null) throw Exception()

            val answer = json.decodeFromJsonElement<BybitTicker>(result)
            if(answer.list.isEmpty()) throw Exception()
            log.debug("Получен ответ от сервера")

            return answer.list.map {
                Course(
                    from = it.symbol,
                    to = "",
                    buy = it.bid1Price,
                    sell = it.ask1Price,
                    price = it.lastPrice
                )
            }
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось получить курсы.\n${msg}")
            throw exception
        }
    }

    override suspend fun fetchCodes(): Set<String> {
        runCatching {
            log.debug("Запрос списка торговых пар...")
            val result = get("/v5/market/instruments-info?category=spot").result
            if(result == null) throw Exception()
            val answer = json.decodeFromJsonElement<BybitInstruments>(result)
            if(answer.list.isEmpty()) throw Exception()
            return answer.list.flatMap {
                listOf(it.baseCoin, it.quoteCoin)
            }.toSet()
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось получить список пар.\n${msg}")
            throw exception
        }
    }

    override suspend fun fetchBalance(): Double {
        runCatching {
            log.debug("Запрос баланса...")
//            val result = get("v5/asset/transfer/query-account-coins-balance", true, mapOf("accountType" to "FUND")).result
//            if(result == null) throw Exception()
//            val answer = json.decodeFromJsonElement<BybitBalance>(result)
//            log.debug("Баланс загружен успешно.")
//            return answer.balance.firstOrNull()?.walletBalance ?: throw ValidationError("Не удается загрузить баланс c Bybit")
            var result = get("/v5/account/wallet-balance", true, mapOf("accountType" to "UNIFIED")).result
            if(result == null) throw Exception()
            val answer = json.decodeFromJsonElement<BybitWallet>(result)
            log.debug("Баланс загружен успешно.")
            return answer.list.firstOrNull()?.totalEquity ?: throw ValidationError("Не удается загрузить баланс c Bybit")

        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось загрузить баланс.\n${msg}")
            throw exception
        }
    }

    override suspend fun receive(quantity: Double, currency: String, chain: String?): PaymentInfo {
        runCatching {
            log.debug("Создание входящего перевода..")
            val currencyObj = getCurrenciesPayin().get(currency.uppercase())
            if (currencyObj == null) throw ValidationError("Валюта ${currency} не поддерживается для входящих переводов")
            val chainObj = if(chain == null) currencyObj.chains.firstOrNull() else currencyObj.chains.firstOrNull { it.code == chain.uppercase() }
            if (chainObj == null) throw ValidationError("Сеть ${chain} не поддерживается для входящих переводов")
            if (quantity !in chainObj.minAmount..chainObj.maxAmount) {
                throw ValidationError("Объем ${currency} должен быть в диапазоне от ${chainObj.minAmount} до ${chainObj.maxAmount}")
            }

            val params = mapOf("coin" to currency.uppercase(), "chainType" to chainObj.code)
            val result = get("/v5/asset/deposit/query-address", true, params).result
            if(result == null) throw Exception()
            val answer = json.decodeFromJsonElement<BybitDeposit>(result)
            val chainAnswer = answer.chains.firstOrNull { it.chain == chainObj.code }
            if(chainAnswer == null) throw ValidationError("Сеть ${chainObj.code} не поддерживается для входящих переводов.")
            if(chainAnswer.batchReleaseLimit != -1 && quantity > chainAnswer.batchReleaseLimit) {
                throw ValidationError("Объем ${currency} для сети ${chainObj.code} превышает лимит ${chainObj.maxAmount}")
            }
            val now = LocalDateTime.now()
            val sameOrders = payinMap.values.filter { it.currency == currency && it.chain == chain &&
                    it.status in listOf("new", "waitingForPayment", "waitingForConfirmation") }
            val payment = PaymentInfo(
                created = now,
                updated = now,
                wallet = chainAnswer.addressDeposit,
                currency = currency,
                chain = chainObj.code,
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

    override suspend fun send(wallet: String, quantity: Double, currency: String, chain: String?, tag: String?): PaymentInfo {
        runCatching {
            log.debug("Создание исходящего перевода..")
            val currencyObj = getCurrenciesPayout().get(currency.uppercase())
            if (currencyObj == null) throw ValidationError("Валюта ${currency} не поддерживается для исходящих переводов")
            val chainObj = if(chain == null) currencyObj.chains.firstOrNull() else currencyObj.chains.firstOrNull { it.code == chain.uppercase() }
            if (chainObj == null) throw ValidationError("Сеть ${chain} не поддерживается для исходящих переводов")
            if (quantity !in chainObj.minAmount..chainObj.maxAmount) {
                throw ValidationError("Объем ${currency} должен быть в диапазоне от ${chainObj.minAmount} до ${chainObj.maxAmount}")
            }

            // Создадим исходящий перевод
            val map = TreeMap<String, Any>()
            map["coin"] = currency.uppercase()
            map["chain"] = chain?.uppercase() ?: throw ValidationError("Для отправки перевода необходимо указать сеть")
            tag?.let { map["tag"] = it }
            map["address"] = wallet
            map["amount"] = quantity
            map["timestamp"] = Instant.now().toEpochMilli() //now.toEpochSecond(ZoneOffset.UTC) * 1000
            map["accountType"] = "FUND"
            val result = post("/v5/asset/withdraw/create", true, map).result
            if(result == null) throw Exception()
            val answer = json.decodeFromJsonElement<BybitWithdraw>(result)

            val now = LocalDateTime.now()
            val payment = PaymentInfo(
                created = now,
                updated = now,
                wallet = wallet,
                currency = currency,
                chain = chainObj.code,
                quantity = quantity,
                uuid = UUID.randomUUID().toString(),
                status = "new"
            )
            payoutMap[payment.uuid] = WithdrawExtendedInfo(answer.id, payment)
            log.info("Исходящий перевод на сумму ${quantity} ${currency} в сети ${chainObj.code} создан.")
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
                if (p.payment.updated.plusSeconds(10L).isAfter(LocalDateTime.now()) || p.payment.status == "completed" ||
                    p.payment.status == "error") return p.payment
                val params = mapOf("id" to p.systemId)
                val result = get("/v5/asset/withdraw/query-record", true, params).result
                if(result == null) throw Exception()
                val answer = json.decodeFromJsonElement<BybitWithdrawStatus>(result)
                val chainObj = answer.rows.firstOrNull { it.withdrawId == p.systemId }
                if(chainObj == null) throw ValidationError("Исходящий перевод не найден")

                p.payment.txid = chainObj.txID
                p.payment.status = when(chainObj.status) {
                    "BlockchainConfirmed" -> "waitingForPayout"
                    "Pending" -> "waitingForPayout"
                    "SecurityCheck" -> "waitingForPayout"
                    "success" -> "completed"
                    "Reject" -> "error"
                    "Fail" -> "error"
                    "CancelByUser" -> "cancelled"
                    "MoreInformationRequired" -> "error"
                    "unknown" -> "error"
                    else -> "new"
                }
                chainObj.withdrawFee.toDoubleOrNull()?.let { p.payment.fee = it }
                p.payment.txid = chainObj.txID
                p.payment.tag = chainObj.tag
                p.payment.updated = LocalDateTime.now()
                return p.payment
            }
            throw ValidationError("Входящий перевод не найден")
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось проверить статус исходящего перевода.\n${msg}")
            throw exception
        }
    }

    override suspend fun checkPayin(uuid: String): PaymentInfo {
        runCatching {
            payinMap[uuid]?.let { payment ->
                if(payment.updated.plusSeconds(10L).isAfter(LocalDateTime.now()) || payment.status == "completed" ||
                    payment.status == "error") return payment
                val params = mutableMapOf(
                    "coin" to payment.currency,
                    "startTime" to payment.created.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
                if(payment.needsTxId) {
                    payment.txid?.let { params["txId"] = it }
                }

                val result = get("/v5/asset/deposit/query-record", true, params).result
                if(result == null) throw Exception()
                val answer = json.decodeFromJsonElement<BybitDepositStatus>(result)
                val chainObj = if(payment.needsTxId) {
                        if(payment.txid != null) answer.rows.firstOrNull { it.txID == payment.txid && it.amount >= payment.quantity }
                        else null
                    }
                    else {
                        answer.rows.firstOrNull { it.coin == payment.currency && it.chain == payment.chain &&
                            it.amount >= payment.quantity }
                    }
                if(chainObj == null) {
                    log.info("Ждем перевода на сумму >= ${payment.quantity} ${payment.currency} в сети ${payment.chain}...")
                    payment.status = "waitingForPayment"
                    return payment
                }

                payment.status = when(chainObj.status) {
                    3 -> "payed"
                    4 -> "error"
                    else -> "waitingForConfirmation"
                }
                chainObj.depositFee.toDoubleOrNull()?.let { payment.fee = it }
                chainObj.confirmations.toIntOrNull()?.let { payment.confirmations = it }
                payment.tag = chainObj.tag
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

    override suspend fun getCurrenciesPayout(): Map<String, PayCurrency> {
        if(payoutPairs.isNotEmpty() && lastPayoutUpdate.plusDays(1L).isAfter(LocalDateTime.now())) {
            return payoutPairs
        }
        runCatching {
            log.debug("Запрос списка валют для отправки переводов...")
            var result = get("/v5/asset/coin/query-info", true).result
            if(result == null) throw Exception()
            val answer = json.decodeFromJsonElement<BybitPayCurrencies>(result)
            val tempMap = answer.rows
                .filter { row -> row.chains.any { it.chainWithdraw == 1 } }
                .associate { row ->
                row.coin to PayCurrency(
                    name = row.name,
                    code = row.coin,
                    chains = row.chains.filter { it.chainWithdraw == 1}.map {
                        PayCurrency.Chain(
                            name = it.chainType,
                            code = it.chain,
                            confirmation = if(it.confirmation.isEmpty()) 0 else it.confirmation.toInt(),
                            fidelity = it.minAccuracy,
                            minAmount = if(it.withdrawMin.isEmpty()) 0.0 else it.withdrawMin.toDouble(),
                            maxAmount = row.remainAmount,
                            fixedFee = if(it.withdrawFee.isEmpty()) 0.0 else it.withdrawFee.toDouble(),
                            percentageFee = if(it.withdrawPercentageFee.isEmpty()) 0.0 else it.withdrawPercentageFee.toDouble()
                        )
                    }
                )
            }
            mutex.withLock {
                payoutPairs.clear()
                payoutPairs.putAll(tempMap)
                lastPayoutUpdate = LocalDateTime.now()
            }
            return tempMap
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось загрузить список валют для отправки переводов.\n${msg}")
            throw exception
        }
    }

    override suspend fun getCurrenciesPayin(): Map<String, PayCurrency> {
        if(payinPairs.isNotEmpty() && lastPayinUpdate.plusDays(1L).isAfter(LocalDateTime.now())) {
            return payinPairs
        }
        runCatching {
            log.debug("Запрос списка валют для приема переводов...")
            var result = get("/v5/asset/coin/query-info", true).result
            if(result == null) throw Exception()
            val answer = json.decodeFromJsonElement<BybitPayCurrencies>(result)
            val tempMap = answer.rows
                .filter { row -> row.chains.any { it.chainDeposit == 1 } }
                .associate { row ->
                row.coin to PayCurrency(
                    name = row.name,
                    code = row.coin,
                    chains = row.chains.filter { it.chainDeposit == 1 }.map {
                        PayCurrency.Chain(
                            name = it.chainType,
                            code = it.chain,
                            confirmation = if(it.confirmation.isEmpty()) 0 else it.confirmation.toInt(),
                            fidelity = it.minAccuracy,
                            minAmount = if(it.depositMin.isEmpty()) 0.0 else it.depositMin.toDouble(),
                            maxAmount = row.remainAmount,
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
            }
            return tempMap
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось загрузить список валют для приема переводов.\n${msg}")
            throw exception
        }
    }

//    override fun cleanOldPayins() {
//        payinMap.values.removeIf { (it.status == PAYMENT_STATUS.FINISHED || it.status == PAYMENT_STATUS.FAILED) &&
//                it.updated.isBefore(LocalDateTime.now().minusMinutes(3L)) }
//        //удалим записи, которые в админке вручную отмечены как выполненные
//        payinMap.values.removeIf { it.updated.isBefore(LocalDateTime.now().minusHours(12L)) }
//    }
//
//    override fun cleanOldPayouts() {
//        payoutMap.values.removeIf { (it.payment.status == PAYMENT_STATUS.FINISHED || it.payment.status == PAYMENT_STATUS.FAILED) &&
//                it.payment.updated.isBefore(LocalDateTime.now().minusMinutes(3L)) }
//        payoutMap.values.removeIf { it.payment.updated.isBefore(LocalDateTime.now().minusHours(12L)) }
//    }

    override suspend fun createInternalTransfer(coin: String, amount: Double, fromAccount: String, toAccount: String) {
        log.info("Создаем внутренний перевод $amount $coin, $fromAccount -> $toAccount")
        val id = UUID.randomUUID().toString()
        val map = TreeMap<String, Any>()
        map["coin"] = coin
        map["fromAccountType"] = fromAccount
        map["toAccountType"] = toAccount
        map["amount"] = amount
        map["transferId"] = id
        val startTime = Instant.now().toEpochMilli()

        post("/v5/asset/transfer/inter-transfer", true, map).result ?: throw ValidationError(
            "Невозможно создать внутренний перевод")

        //ждем обработки перевода
        var attempts = 0
        val maxAttempts = 6 //30 сек
        while (attempts < maxAttempts) {
            delay(5000L)
            val answer = get("/v5/asset/transfer/query-inter-transfer-list", true, mapOf("transferId" to id,
                "startTime" to startTime)).result ?: throw ValidationError("Невозможно проверить внутренний перевод")
            val status = json.decodeFromJsonElement<BybitInternalTransferStatus>(answer).list
                .firstOrNull{ it.transferId == id }?.status
            if(status == null || status == "FAILED") throw ValidationError("Невозможно проверить внутренний перевод")
            if(status == "SUCCESS") {
                log.info("Внутренний перевод $amount $coin, $fromAccount -> $toAccount выполнен")
                return
            }
            attempts++
        }
        throw ValidationError("Внутренний перевод $amount $coin $fromAccount -> $toAccount не выполнен за 30 секунд")
    }

    override suspend fun swap(from: String, to: String, side: String, amountFrom: Double?, amountTo: Double?): SwapResult {
        val map = TreeMap<String, Any>()
        map["category"] = "spot"
        map["symbol"] = "$from$to"
        map["side"] = side
        map["orderType"] = "Market"
        val limits = getTradeLimits(from, to)
        if(amountFrom != null) {
            map["marketUnit"] = "baseCoin"
            val amountRounded = amountFrom.roundToStep(limits.baseStep)
            map["qty"] = amountRounded
            log.info("Меняем: $side $amountRounded $from за $to")
        }
        else if(amountTo != null) {
            map["marketUnit"] = "quoteCoin"
            val amountRounded = amountTo.roundToStep(limits.quoteStep)
            map["qty"] = amountRounded
            log.info("Меняем: $side $amountRounded $to за $from")
        }
        else throw ValidationError("Не указана сумма для конвертации $from -> $to")
        val answer = post("/v5/order/create", true, map).result
            ?: throw ValidationError("Невозможно создать ордер для конвертации $from -> $to")
        val id = json.decodeFromJsonElement<BybitCreateOrder>(answer).orderId

        //ждем обработки ордера
        var attempts = 0
        val maxAttempts = 6 //30 сек
        while (attempts < maxAttempts) {
            delay(5000L)
            val answer = get("/v5/order/history", true, mapOf("category" to "spot", "orderId" to id)).result
                ?: throw ValidationError("Невозможно проверить ордер при конвертации $from -> $to")

            val result = json.decodeFromJsonElement<BybitOrderStatus>(answer).list
                .firstOrNull { it.orderId == id } ?: throw ValidationError("Ордер не найден для $from -> $to")
            if(result.orderStatus == "Cancelled" || result.orderStatus == "Rejected") throw ValidationError("Ордер отклонен для $from -> $to")

            if(result.orderStatus == "Filled") {
                val price = result.avgPrice.toDoubleOrNull()
                    ?: throw ValidationError("Цена неизвестна для $from -> $to")
                log.debug("Конвертация $from -> $to по курсу $price выполнена")
                val execQty = result.cumExecQty.toDoubleOrNull() ?: throw ValidationError("Объем ордера неизвестен для $from -> $to")
                return SwapResult(price, execQty, result.cumExecFee.toDoubleOrNull() ?: 0.0)
            }
            attempts++
        }
        throw ValidationError("Конвертация $from -> $to не завершена за 30 секунд")
    }

    override suspend fun getTradeLimits(from: String, to: String): TradeLimits {
        runCatching {
            log.debug("Запрос лимитов для торгов на Spot...")
            val pair = "$from$to".uppercase()
            var result = get("/v5/market/instruments-info", false, mapOf("category" to "spot", "symbol" to pair)).result
            if(result == null) throw Exception()
            val answer = json.decodeFromJsonElement<BybitInstruments>(result).list.firstOrNull { it.baseCoin == from.uppercase()
                    && it.quoteCoin == to.uppercase() }?.let {
                TradeLimits(minValue = it.lotSizeFilter.minOrderAmt, maxValue = it.lotSizeFilter.maxOrderAmt,
                    minQty = it.lotSizeFilter.minOrderQty, maxQty = it.lotSizeFilter.maxOrderQty,
                    baseStep = it.lotSizeFilter.basePrecision, quoteStep = it.lotSizeFilter.quotePrecision)
            }
            return answer ?: throw ValidationError("Лимиты для $from -> $to неизвестны")
        }.getOrElse { exception ->
            val msg = exception.message ?: "Неизвестная ошибка"
            log.error("Не удалось проверить лимиты для торгов на Spot.\n${msg}")
            throw exception
        }
    }

    // комиссия за рыночный ордер для пользователя
    override suspend fun getFeeRate(from: String, to: String): Double {
        val symbol = "$from$to".uppercase()
        feeRates[symbol]?.let { return it }
        val params = mapOf("category" to "spot", "symbol" to symbol)
        val response = get("/v5/account/fee-rate", true, params).result
            ?: throw ValidationError("Не удалось получить комиссию для $symbol")
        val feeData = json.decodeFromJsonElement<BybitFeeRateResponse>(response)
        val fee = feeData.list.firstOrNull { it.symbol == symbol }?.takerFeeRate?.toDoubleOrNull()
            ?: throw ValidationError("Комиссия для $symbol не найдена")
        feeRates[symbol] = fee
        return fee
    }

    override suspend fun saveTxId(uuid: String, txid: String) {
        payinMap[uuid]?.let {
            if(it.status != "completed" && it.status != "error" && it.status != "cancelled") it.txid = txid
            else throw ValidationError("Невозможно сохранить txid - входящий перевод завершен")
            return
        }
        throw ValidationError("Невозможно сохранить txid - входящий перевод не найден")
    }

    override fun deletePayin(uuid: String) {
        payinMap.remove(uuid)
    }

    override fun deletePayout(uuid: String) {
        payoutMap.remove(uuid)
    }
}

@Serializable
data class BybitAnswer(
    val retCode: Int,
    val retMsg: String,
    val result: JsonElement? //здесь разные типы, парсим их позже
)

@Serializable
data class BybitTicker(
    val category: String,
    val list: List<Ticker>
) {
    @Serializable
    data class Ticker(
        val symbol: String,
        @Contextual val bid1Price: Double,
        @Contextual val ask1Price: Double,
        @Contextual val lastPrice: Double
    )
}

@Serializable
data class BybitInstruments(
    val category: String,
    val list: List<Ticker>
) {
    @Serializable
    data class Ticker(
        val symbol: String,
        val baseCoin: String,
        val quoteCoin: String,
        val lotSizeFilter: Lot
    ) {
        @Serializable
        data class Lot(
            val basePrecision: Double,
            val quotePrecision: Double,
            val minOrderQty: Double,
            val maxOrderQty: Double,
            val minOrderAmt: Double,
            val maxOrderAmt: Double
        )
    }
}

@Serializable
data class BybitBalance(
    val balance: List<Balance>
) {
    @Serializable
    data class Balance(
        val coin: String,
        val walletBalance: Double,
        val transferBalance: Double
    )
}

@Serializable
data class BybitWallet(
    val list: List<Balance>
) {
    @Serializable
    data class Balance(
        val totalEquity: Double
    )
}

@Serializable
data class BybitPayCurrencies(
    val rows: List<Currency>
) {
    @Serializable
    data class Currency(
        val name: String, //название валюты
        val coin: String, //код валюты в верхнем регистре
        val remainAmount: Double, //макс сумма вывода за одну транзакцию
        val chains: List<Chain>
    ) {
        @Serializable
        // пустая строка = 0
        data class Chain(
            val chainType: String, //название сети
            val chain: String, //код сети в верхнем регистре
            val confirmation: String, //если пустая - 0 подтверждений
            val minAccuracy: Double, //точность, знаков после запятой
            val chainDeposit: Int, //входящие платежи доступны? 0/1
            val chainWithdraw: Int, //исходящие платежи доступны? 0/1
            val depositMin: String, //мин объем
            val withdrawMin: String, //мин объем
            val withdrawFee: String, //комиссия вывода, если пустая - вывод недоступен
            val withdrawPercentageFee: String //комиссия за вывод, в % (коэффициент для умножения)
        )
    }
}

@Serializable
data class BybitDeposit(
    val coin: String,
    val chains: List<Chain>
) {
    @Serializable
    data class Chain(
        val chainType: String,
        val chain: String,
        val addressDeposit: String,
        val tagDeposit: String,
        val batchReleaseLimit: Int
    )
}

@Serializable
data class BybitDepositStatus(
    val rows: List<Currency>
) {
    @Serializable
    data class Currency(
        val coin: String,
        val chain: String,
        val amount: Double,
        val txID: String,
        val status: Int,
        val tag: String,
        val depositFee: String,
        val confirmations: String,
    )
}

@Serializable
data class BybitWithdraw(
    val id: String
)

data class WithdrawExtendedInfo(
    val systemId: String,
    val payment: PaymentInfo
)

@Serializable
data class BybitWithdrawStatus(
    val rows: List<Withdraw>
) {
    @Serializable
    data class Withdraw(
        val coin: String,
        val chain: String,
        val amount: Double,
        val txID: String,
        val status: String,
        val tag: String,
        val toAddress: String,
        val withdrawFee: String,
        val withdrawId: String
    )
}

@Serializable
data class BybitCreateOrder(
    val orderId: String
)

@Serializable
data class BybitOrderStatus(
    val list: List<Order>
) {
    @Serializable
    data class Order(
        val orderId: String,
        val orderStatus: String,
//        Спасибо Bybit за унификацию. это же полный бардак!
//        val price: String,
//        val basePrice: String,
        val avgPrice: String,
        val cumExecQty: String,
        val cumExecFee: String
    )
}

@Serializable
data class BybitInternalTransferStatus(
    val list: List<Transfer>
) {
    @Serializable
    data class Transfer(
        val transferId: String,
        val status: String
    )
}

@Serializable
data class BybitFeeRateResponse(
    val list: List<FeeRate>
) {
    @Serializable
    data class FeeRate(
        val symbol: String,
        val takerFeeRate: String,
        val makerFeeRate: String
    )
}