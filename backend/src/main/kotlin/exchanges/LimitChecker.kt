package com.github.perelshtein.exchanges

import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.CurrencyManager
import com.github.perelshtein.database.DirectionDTO
import com.github.perelshtein.routes.ConvertPrice
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import kotlin.getValue
import kotlin.math.max
import kotlin.math.min

// проверка лимитов по валютам
class LimitChecker : KoinComponent {
    val currencyMgr: CurrencyManager by inject()
    val autopay: Autopay by inject()
    val log = LoggerFactory.getLogger("LimitChecker")

    suspend fun validateLimits(
        amountParam: Double,
        direction: DirectionDTO, //направление из базы данных, или новое
        currencyCode: String, // валюта, в которую конвертируем
        chainCode: String,
        exchange: String,
//        isCheckOrder: Boolean = false,
        isCheckDirGet: Boolean? = null, // true = направление входящее, false = направление исходящее, null = заявка
        alerts: MutableList<String>? = null,
    ) {

        // 1) Конвертируем мин/макс направления в USDT
        val minCurrency = currencyMgr.getCurrencyById(direction.minSumCurrencyId) ?: throw ValidationError("Валюта min не найдена")
        val maxCurrency = currencyMgr.getCurrencyById(direction.maxSumCurrencyId) ?: throw ValidationError("Валюта max не найдена")
        val a = convertToCurrency(
            amount = direction.minSum,
            fromCurrency = minCurrency.code,
            toCurrency = "USDT",
            alerts = alerts,
            type = "мин.сумму"
        )
        val b = convertToCurrency(
            amount = direction.maxSum,
            fromCurrency = maxCurrency.code,
            toCurrency = "USDT",
            alerts = alerts,
            type = "макс.сумму"
        )
        val minUsdt = min(a, b)
        val maxUsdt = max(a, b)

        // 2) Конвертируем сумму, которую проверяем, в USDT
        val amountUsdt = convertToCurrency(
            amount = amountParam,
            fromCurrency = currencyCode,
            toCurrency = "USDT",
            alerts = alerts,
            type = "сумму"
        )

        val giveCurrency = currencyMgr.getCurrencyById(direction.fromId) ?: throw ValidationError("Валюта Отдаю не найдена")
        val getCurrency = currencyMgr.getCurrencyById(direction.toId) ?: throw ValidationError("Валюта Получаю не найдена")

        // Для пользователя (проверка заявки):
        // 3) Cравнить сумму в USDT с мин и макс лимитом
        if(isCheckDirGet == null) {
            if (amountUsdt < minUsdt) {
                val giveLimit = convertToCurrency(
                    amount = minUsdt,
                    fromCurrency = "USDT",
                    toCurrency = giveCurrency.code,
                    alerts = alerts,
                    type = "мин.сумму"
                )
                throw ValidationError("Минимальная сумма перевода должна быть не менее ${giveLimit} ${giveCurrency.code}")
            }
            if (amountUsdt > maxUsdt) {
                val getLimit = convertToCurrency(
                    amount = maxUsdt,
                    fromCurrency = "USDT",
                    toCurrency = getCurrency.code,
                    alerts = alerts,
                    type = "макс.сумму"
                )
                throw ValidationError("Максимальная сумма перевода должна быть не более ${getLimit} USDT")
            }
            return
        }

        // Для админа (редактирование направления):
        // 3) проверим лимиты сети и торговые лимиты

        // рубль? пошел нафиг
        val checkCurrency = if (isCheckDirGet) getCurrency else giveCurrency
        val type = if (isCheckDirGet == true) "Исходящий" else "Входящий"
        val sendType = if (isCheckDirGet == true) "ручной отправки" else "ручного приема"
        if(exchange == "manual") {
            log.info("$type ${checkCurrency.code}: Пропускаем проверку лимитов для ${sendType}")
            return
        }
        if (checkCurrency.code == "RUB") {
            log.info("$type ${checkCurrency.code}: Пропускаем проверку лимитов для фиатной валюты.")
            return
        }

        //Лимиты на переводы
        val payin = autopay.getPayin(exchange)
        val payout = autopay.getPayout(exchange)
        val currencyData = if (isCheckDirGet) {
            payout.getCurrenciesPayout().entries.firstOrNull { it.key == getCurrency.code }?.value
                ?: throw ValidationError("Валюта ${getCurrency.code} не поддерживается для исходящих переводов на $exchange")
        } else {
            payin.getCurrenciesPayin().entries.firstOrNull { it.key == giveCurrency.code }?.value
                ?: throw ValidationError("Валюта ${giveCurrency.code} не поддерживается для входящих переводов на $exchange")
        }

        val chain = if(isCheckDirGet) {
            currencyData.chains.firstOrNull { it.code == getCurrency.payoutChain }
                ?: throw ValidationError("Сеть ${getCurrency.payoutChain} для ${checkCurrency.code} не поддерживается на $exchange")
        } else {
            currencyData.chains.firstOrNull { it.code == giveCurrency.payinChain }
                ?: throw ValidationError("Сеть ${giveCurrency.payinChain} для ${checkCurrency.code} не поддерживается на $exchange")
        }

        val chainMinUsdt = convertToCurrency(
            amount = chain.minAmount,
            fromCurrency = checkCurrency.code,
            toCurrency = "USDT",
            alerts = alerts,
            type = "мин.для сети"
        )
        val chainMaxUsdt = convertToCurrency(
            amount = chain.maxAmount,
            fromCurrency = checkCurrency.code,
            toCurrency = "USDT",
            alerts = alerts,
            type = "макс.для сети"
        )

        if (amountUsdt < chainMinUsdt) {
            throw ValidationError("Мин сумма перевода должна быть не менее ${chain.minAmount} ${checkCurrency.code}")
        }
        if (amountUsdt > chainMaxUsdt) {
            throw ValidationError("Макс сумма перевода должна быть не более ${chain.maxAmount} ${checkCurrency.code}")
        }

        //Лимиты на торги
        if(checkCurrency.code == "USDT") return
        val tradeLimits = payin.getTradeLimits(checkCurrency.code, "USDT")
        if (amountUsdt < tradeLimits.minValue) {
            throw ValidationError("Минимальный объем для торгов должен быть не менее ${tradeLimits.minQty} ${checkCurrency.code}")
        }
        if (amountUsdt > tradeLimits.maxValue) {
            throw ValidationError("Максимальный объем для торгов должен быть не более ${tradeLimits.maxQty} ${checkCurrency.code}")
        }
    }

    suspend fun convertToCurrency(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        alerts: MutableList<String>?,
        type: String
    ): Double {
        val convertPrice: ConvertPrice by inject()
        if (fromCurrency == toCurrency) return amount

        val converted = convertPrice.convertAmount(amount, fromCurrency, toCurrency)
        if (converted == 0.0) {
            alerts?.add("Не удалось проверить $type в $amount $fromCurrency, " +
                    "кросс-курс $fromCurrency -> $toCurrency не найден.")
        }
        return converted
    }
}