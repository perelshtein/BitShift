package com.github.perelshtein

import com.github.perelshtein.database.CurrencyManager
import com.github.perelshtein.database.NotifyBindManager
import com.github.perelshtein.database.NotifyDTO
import com.github.perelshtein.database.OrdersRecord
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class NotifySender: KoinComponent {
    private val mail: Mail by inject()
    private val tg: TelegramBot by inject()
    private val adminNotify: NotifyBindManager by inject(named("admin"))
    private val userNotify: NotifyBindManager by inject(named("user"))
    private val optionsMgr: OptionsManager by inject()
    private val accessControl: AccessControl by inject()

    private fun updateTags(text: String, order: OrdersRecord): String {
        val currencyMgr: CurrencyManager by inject()
        val give = currencyMgr.getCurrencyByXmlCode(order.fromXmlCode)
        val get = currencyMgr.getCurrencyByXmlCode(order.toXmlCode)
        val profitUsdt = if(order.rateFrom == 0.0 || order.amountFrom == 0.0 || order.profit == 0.0) 0.0
            else (order.profit / (order.amountFrom * order.rateFrom) * 100).roundup(2)
        return text.replaceTags(mapOf(
            "id" to "${order.id}",
            "requisites" to "${order.requisites}",
            "profitPercent" to "${order.profit}",
            "profitUsdt" to "${profitUsdt}",
            "email" to (accessControl.getUserById(order.userId)?.mail ?: "Почта не указана"),
            "walletFrom" to "${order.walletFrom}",
            "walletTo" to "${order.walletTo}",
            "amountFrom" to "${order.amountFrom}",
            "amountTo" to "${order.amountTo}",
            "receiveType" to if(order.isManualReceive) "Ручной" else "Авто",
            "sendType" to if(order.isManualSend) "Ручной" else "Авто",
            "currencyFrom" to "${order.fromName} ${order.fromCode} ${give?.payinChain}",
            "currencyTo" to "${order.fromName} ${order.fromCode} ${get?.payoutChain}",
        ))
    }

    suspend fun send(order: OrdersRecord) {
        adminNotify.getStatus(order.status)?.let {
            val textUpdated = updateTags(it.text, order)
            val subjUpdated = updateTags(it.subject, order)
            tg.send(textUpdated)
            val adminMail = optionsMgr.getOptions().adminEmail
            if(adminMail.isNotBlank()) {
                mail.send(adminMail, subjUpdated, textUpdated)
            }
        }

        userNotify.getStatus(order.status)?.let {
            val textUpdated = updateTags(it.text, order)
            val subjUpdated = updateTags(it.subject, order)
            val userMail = accessControl.getUserById(order.userId)?.mail
            if(!userMail.isNullOrBlank()) {
                mail.send(userMail, subjUpdated, textUpdated)
            }
        }
    }
}