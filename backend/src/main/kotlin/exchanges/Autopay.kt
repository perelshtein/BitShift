package com.github.perelshtein.exchanges

import com.github.perelshtein.AccessControl
import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.CurrencyManager
import com.github.perelshtein.database.ORDER_SRC
import com.github.perelshtein.database.OrdersManager
import com.github.perelshtein.database.OrdersRecord
import com.github.perelshtein.database.getTimeInterval
import com.github.perelshtein.database.toAdminDTO
import com.github.perelshtein.roundup
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.getValue

class Autopay: KoinComponent {
    private val orders = ConcurrentHashMap<Int, UserOrder>()
    private val ordersMgr: OrdersManager by inject()
    private val currencyMgr: CurrencyManager by inject()
    private val log = LoggerFactory.getLogger("Autopay")
    private val usersMgr: AccessControl by inject()

    fun getPayin(name: String): PayInAPI {
        val bybit: PayInAPI by inject<Bybit>()
        val mexc: PayInAPI by inject<Mexc>()
        return when(name.uppercase()) {
            "BYBIT" -> bybit
            "MEXC" -> mexc
            else -> throw ValidationError("Неизвестная биржа $name")
        }
    }

    fun getPayout(name: String): PayOutAPI {
        val bybit: PayOutAPI by inject<Bybit>()
        val mexc: PayOutAPI by inject<Mexc>()
        return when(name.uppercase()) {
            "BYBIT" -> bybit
            "MEXC" -> mexc
            else -> throw ValidationError("Неизвестная биржа $name")
        }
    }

    suspend fun addOrder(userId: Int) {
        val userName = usersMgr.getUserById(userId)?.name ?: "Неизвестный пользователь"
        val freshOrder = ordersMgr.getActiveOrder(userId) ?: throw ValidationError("Активная заявка не найдена")
        runCatching {
            val currency =
                currencyMgr.getCurrencyByXmlCode(freshOrder.fromXmlCode) ?: throw ValidationError("Валюта Отдаю не найдена")
            if (currency.payin != "manual") {
                val payin = getPayin(currency.payin)
                val from = payin.receive(freshOrder.amountFrom, currency.payinCode, currency.payinChain)
                orders[userId] = UserOrder(uuidReceive = from.uuid, exchangeReceive = currency.payin)
                ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY, freshOrder.apply {
                    walletFrom = from.wallet
                    status = "waitingForPayment"
                    isNeedsTxId = from.needsTxId
                    if(currency.code == "USDT") rateFrom = 1.0
                })
                log.info("Создан входящий платеж на ${freshOrder.amountFrom} ${currency.code} для пользователя ${userName}")
            } else {
                orders[userId] = UserOrder(exchangeReceive = "manual")
                log.info("Ждем оператора: Входящий платеж на ${freshOrder.amountFrom} ${currency.code} для пользователя ${userName}")
            }
        }.onFailure {
            log.error("Не удалось создать входящий платеж для пользователя ${userName}: ${it.message}")
            ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY, freshOrder.apply {
                this.status = "error"
            })
        }
    }

    private suspend fun addPayout(userId: Int) {
        val userName = usersMgr.getUserById(userId)?.name ?: "Неизвестный пользователь"
        val freshOrder = ordersMgr.getActiveOrder(userId) ?: throw ValidationError("Активная заявка не найдена")
        try {
            val currency =
                currencyMgr.getCurrencyByXmlCode(freshOrder.toXmlCode) ?: throw ValidationError("Валюта Получаю не найдена")
            if (currency.payout != "manual") {
                val payin = getPayin(currency.payin)
                val payout = getPayout(currency.payout)
                if(currency.code != "USDT") {
                    val usdt = currencyMgr.getCurrencyByCode("USDT") ?: throw ValidationError("Валюта USDT не найдена")
                    val fixedFee = payout.getCurrenciesPayout()[currency.code]?.chains?.firstOrNull { it.code == currency.payoutChain }?.fixedFee
                        ?: throw ValidationError("Комиссия для ${currency.code} в сети ${currency.payoutChain} не найдена")
                    val percentageFee = payout.getCurrenciesPayout()[currency.code]?.chains?.firstOrNull { it.code == currency.payoutChain }?.percentageFee
                        ?: throw ValidationError("Комиссия для ${currency.code} в сети ${currency.payoutChain} не найдена")
                    // плата за отправку + плата за обмен на spot
                    val commission = fixedFee + freshOrder.amountTo * (percentageFee + payin.getFeeRate(currency.code, "USDT")) //в DOGE
                    val usdtAmount = (freshOrder.amountFrom * freshOrder.rateFrom).roundup(usdt.fidelity)
                    val amountTo = (freshOrder.amountTo + commission).roundup(currency.fidelity) //commission added

                    payin.createInternalTransfer("USDT", usdtAmount, "FUND", "UNIFIED")
                    val obmen = payin.swap(currency.code, "USDT", "Buy", amountTo) //для DOGEUSDT, количество задаем в DOGE
                    val spentUsdt = (amountTo * obmen.price).roundup(usdt.fidelity)
                    val remainder = (usdtAmount - spentUsdt).roundup(usdt.fidelity)

                    if (remainder < 0) {
                        log.error("Недостаточно USDT для выплаты: требуется $spentUsdt, доступно $usdtAmount," +
                                "пользователь $userName")
                        payin.createInternalTransfer(currency.code, obmen.execQty - obmen.orderFee, "UNIFIED", "FUND") // Rollback DOGE
                        deleteOrder(userId, "cancelledUnprofitable")
                        log.error("Недостаточно средств из-за изменения курса")
                        return
                    }

                    ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY,freshOrder.apply {
                        rateTo = obmen.price
                        // комиссию запишем позже, при закрытии заявки, чтобы учесть реальную комиссию
                        // (она может меняться при загрузке сети)
                    })
                    payin.createInternalTransfer(currency.code, obmen.execQty - obmen.orderFee, "UNIFIED", "FUND")
                    if (remainder > 0) {
                        log.info("Остаток $remainder USDT")
                        payin.createInternalTransfer("USDT", remainder, "UNIFIED", "FUND")
                    }
                }
                else ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY, freshOrder.apply {
                    rateTo = 1.0
                } )
                val tag = freshOrder.toAdminDTO().fieldsGet.getOrDefault("tag", null)
                val to = payout.send(freshOrder.walletTo, freshOrder.amountTo.roundup(currency.fidelity),
                    currency.payoutCode, currency.payoutChain, tag) //sent funds to user without commission
//                ordersMgr.upsertOrder(freshOrder.apply {
//                    dateStatusUpdated = to.updated
//                } )
                val old = orders[userId]
                orders[userId] = UserOrder(exchangeReceive = old?.exchangeReceive ?: currency.payin,
                    uuidSend = to.uuid, exchangeSend = currency.payout)
                log.info("Создан исходящий платеж на ${freshOrder.amountTo.roundup(currency.fidelity)} ${currency.code} для пользователя ${userName}")
            } else {
                orders[userId] = UserOrder(exchangeSend = "manual")
                log.info("Ждем оператора: Исходящий платеж на ${freshOrder.amountTo} ${currency.code} для пользователя ${userName}")
            }
        } catch(e: Exception) {
            log.error("Не удалось создать исходящий платеж для пользователя ${userName}: ${e.message}")
            ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY,freshOrder.apply {
                this.status = "error"
            })
        } finally {
            orders[userId]?.let {
                val cachedId = it.uuidReceive
                it.uuidReceive = null
                if(it.exchangeReceive != "manual" && cachedId != null) {
                    val payin = getPayin(it.exchangeReceive)
                    payin.deletePayin(cachedId)
                }
            }
        }
    }

    suspend fun calcProfit(order: OrdersRecord) {
        log.info("Расчет прибыли..")
        if(order.rateFrom == 0.0 || order.rateTo == 0.0) return
        val receivedValue = order.amountFrom * order.rateFrom
        val sentValue = order.amountTo * order.rateTo
        val totalCosts = sentValue + order.payinFee + order.payoutFee
        val fidelity = currencyMgr.getCurrencyByCode("USDT")?.fidelity ?: throw ValidationError("Расчет прибыли: Неизвестная валюта USDT")
        val profitUsdt = (receivedValue - totalCosts).roundup(fidelity)
        val profitPercent = (profitUsdt / receivedValue * 100).roundup(fidelity)
        val userName = usersMgr.getUserById(order.userId)?.name ?: "Неизвестный пользователь"
        log.info("Прибыль от пользователя ${userName} по заявке ${order.id}: ${profitUsdt} USDT (${profitPercent}%)")
        order.profit = profitUsdt
        ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY, order)
        return
    }

    suspend fun deleteOrder(userId: Int, statusParam: String = "deleted") {
        val userName = usersMgr.getUserById(userId)?.name ?: "Неизвестный пользователь"
        orders[userId]?.uuidReceive?.let {
            val payin = getPayin(orders[userId]!!.exchangeReceive)
            payin.deletePayin(it)
            log.debug("Входящий перевод отменен для пользователя $userName")
        }
        orders[userId]?.uuidSend?.let {
            val payout = getPayout(orders[userId]!!.exchangeSend)
            payout.deletePayout(it)
            log.debug("Исходящий перевод отменен для пользователя $userName")
        }
        orders.keys.removeIf {
            log.info("Удалена активная заявка для пользователя $userName")
            it == userId
        }
        ordersMgr.getActiveOrder(userId)?.let {
            ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY, it.apply {
                this.status = statusParam
            })
        }
    }

    suspend fun check() {
        for ((k, v) in orders) {
            ordersMgr.getActiveOrder(k)?.let { activeOrder ->
                runCatching {
                    var givePayment: PaymentInfo? = null
                    var getPayment: PaymentInfo? = null
                    if (v.exchangeReceive == "manual") {
                        //в админке выставили: Оплачено, входящий перевод выполнен
                        if (activeOrder.status == "payed") {
                            v.exchangeReceive = ""
                            //создадим исходящий перевод
                            addPayout(k)
                        }
                    }
                    else {
                        v.uuidReceive?.let {
                            val payin = getPayin(orders[k]!!.exchangeReceive)
                            givePayment = payin.checkPayin(it)
                            ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY, activeOrder.apply {
                                this.status = givePayment.status
                                this.payinFee = givePayment.fee
                                this.isNeedsTxId = this.isNeedsTxId || givePayment.needsTxId
                                this.walletFrom = givePayment.wallet
                            })
                            if (givePayment.status == "payed") {
                                val give = currencyMgr.getCurrencyByXmlCode(activeOrder.fromXmlCode)
                                    ?: throw ValidationError("Валюта Отдаю не найдена")
                                val usdt =
                                    currencyMgr.getCurrencyByCode("USDT")
                                        ?: throw ValidationError("Валюта USDT не найдена")
                                // входящую валюту конвертируем в USDT
                                val amountUsdt = if (give.code != "USDT") {
                                    payin.createInternalTransfer(
                                        give.code,
                                        activeOrder.amountFrom.roundup(give.fidelity),
                                        "FUND",
                                        "UNIFIED"
                                    )
                                    val result =
                                        payin.swap(
                                            give.code,
                                            "USDT",
                                            "Sell",
                                            activeOrder.amountFrom.roundup(give.fidelity)
                                        )
                                    ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY, activeOrder.apply {
                                        this.rateFrom = result.price
                                        this.payinFee = result.orderFee //продаю - комиссия в USDT
                                    })
                                    //можно использовать execQty, но не будем - оставим USDT на споте для оплаты комиссии по след сделкам
                                    val amountUsdt = (activeOrder.amountFrom * result.price).roundup(usdt.fidelity)
                                    payin.createInternalTransfer(
                                        "USDT",
                                        amountUsdt,
                                        "UNIFIED",
                                        "FUND"
                                    )
                                    amountUsdt
                                } else activeOrder.amountFrom

                                ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY, activeOrder.apply { orderValue = amountUsdt })
                                addPayout(k)
                            }
                        }
                    }

                    if (v.exchangeSend == "manual" && activeOrder.status == "completed") {
                        calcProfit(activeOrder)
                        deleteOrder(k)
                    }
                    else {v.uuidSend?.let {
                        val payin = getPayin(orders[k]!!.exchangeSend)
                        val payout = getPayout(orders[k]!!.exchangeSend)
                        getPayment = payout.checkPayout(it)
                        val updatedOrder = activeOrder.apply {
                            this.status = getPayment.status
                            if (getPayment.status == "completed") {
                                //TODO: добавить определение фиатных валют
                                val tradeFee = if(activeOrder.fromCode != "RUB") payin.getFeeRate(activeOrder.fromCode, "USDT") else 0.0 // 0.0018 = 0.18%
                                // 4 DOGE -> N%, реальная комиссия (зависит от загрузки сети)
                                val payoutFeeCoeff = getPayment.fee / activeOrder.amountTo // коэфф
                                val outUsdt = activeOrder.amountTo * activeOrder.rateTo
                                this.payoutFee = outUsdt * (tradeFee + payoutFeeCoeff)
                            }
                        }
                        ordersMgr.upsertOrder(ORDER_SRC.AUTOPAY,updatedOrder)
                        if (getPayment.status == "completed") {
                            calcProfit(updatedOrder)
                            deleteOrder(k) // удаляем сразу, иначе ждать +1мин для автоплатежей
                        }
                    }}

                    // заявки отменили вручную
                    if (activeOrder.status == "cancelled" || activeOrder.status == "deleted") {
                        deleteOrder(k)
                    }

                    cleanInactiveOrders(k, v, givePayment, getPayment)
                }.onFailure {
                    log.error("Ошибка по заявке ${activeOrder.id}", it)
                    deleteOrder(k, "error")
                }
            }
        }
    }

    suspend fun saveTxId(userId: Int, txId: String) {
        val uuid = orders[userId]?.uuidReceive ?: throw ValidationError("Пользователь не найден")
        val payin = getPayin(orders[userId]?.exchangeReceive ?: throw ValidationError("Биржа не найдена"))
        payin.saveTxId(uuid, txId)
    }

    suspend fun cleanInactiveOrders(k: Int, v: UserOrder, updatedFrom: PaymentInfo?, updatedTo: PaymentInfo?) {
        val now = LocalDateTime.now()
        // проверим последний статус входящего или исходящего перевода, или статус из БД
        // если превышен интервал - удалим
        ordersMgr.getActiveOrder(k)?.let { dbOrder ->
//            val lastStatus = updatedFrom?.status ?: updatedTo?.status ?: dbOrder.status
//            if(dbOrder.status != lastStatus) {
                val timeoutMinutes = dbOrder.getTimeInterval()
                if (dbOrder.dateStatusUpdated.isBefore(now.minusMinutes(timeoutMinutes.toLong()))) {
                    val userName = usersMgr.getUserById(k)?.name ?: "Неизвестный пользователь"
                    log.info("Удаление заявки по таймауту ${timeoutMinutes} мин, пользователь ${userName}")
                    deleteOrder(k)
                }
//            }
        }
    }
}

interface PayInAPI {
    suspend fun receive(quantity: Double, currency: String, chain: String?): PaymentInfo
    suspend fun checkPayin(uuid: String): PaymentInfo
    suspend fun getCurrenciesPayin(): Map<String, PayCurrency>
    suspend fun createInternalTransfer(coin: String, amount: Double, fromAccount: String, toAccount: String)
    suspend fun swap(from: String, to: String, side: String, amountFrom: Double? = null, amountTo: Double? = null): SwapResult
    suspend fun getTradeLimits(from: String, to: String): TradeLimits
    suspend fun getFeeRate(from: String, to: String): Double // комиссия за торги на spot, не привязана к входящим или исходящим
    suspend fun saveTxId(uuid: String, txid: String)
//    fun cleanOldPayins()
    fun deletePayin(uuid: String)
}

interface PayOutAPI {
    suspend fun send(wallet: String, quantity: Double, currency: String, chain: String? = null, tag: String? = null): PaymentInfo
    suspend fun checkPayout(uuid: String): PaymentInfo
    suspend fun getCurrenciesPayout(): Map<String, PayCurrency>
    fun deletePayout(uuid: String)
//    fun cleanOldPayouts()
}

data class PaymentInfo(
    val created: LocalDateTime,
    var updated: LocalDateTime,
    val wallet: String,
    val currency: String,
    val chain: String,
    val quantity: Double,
    val uuid: String,
    var status: String,
    var txid: String? = null,
    var confirmations: Int? = null,
    var tag: String? = null,
    var fee: Double = 0.0,
    var needsTxId: Boolean = false
)

//enum class PAYMENT_STATUS {
//    NEW,
//    PENDING,
//    WAITING_FOR_CONFIRMATION,
//    FINISHED,
//    FAILED
//}

@Serializable
data class PayCurrency(
    val name: String, //название валюты или название банка
    val code: String, //код валюты в верхнем регистре
    val chains: List<Chain>
) {
    @Serializable
    data class Chain(
        val name: String, //имя валюты
        val code: String, //код сети в верхнем регистре
        val advancedCode: String = "", //для MEXC
        val confirmation: Int,
        val fidelity: Double,
        val minAmount: Double,
        val maxAmount: Double,
        val fixedFee: Double,
        val percentageFee: Double,
        val currencyCode: String = "" //специально для MEXC. мудозвоны
    )
}

data class UserOrder(
    var uuidReceive: String? = null,
    var uuidSend: String? = null,
    var exchangeReceive: String = "",
    var exchangeSend: String = ""
)

data class TradeLimits(
    val minValue: Double, // в USDT (amount)
    val maxValue: Double,
    val minQty: Double, // in base-currency
    val maxQty: Double,
    val baseStep: Double,
    val quoteStep: Double
)

data class SwapResult(
    val price: Double,     // avgPrice from order
    val execQty: Double,   // actual qty executed (cumExecQty)
    val orderFee: Double   // fee in USDT (cumExecFee)
)