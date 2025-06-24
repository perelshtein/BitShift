package com.github.perelshtein

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

class TelegramBot: KoinComponent, TelegramLongPollingBot() {
    private val log = LoggerFactory.getLogger("TelegramBot")
    private val optionsMgr: OptionsManager by inject()

    init {
        if(optionsMgr.getTelegramToken().isNotBlank() && optionsMgr.getOptions().telegramBotName.isNotBlank()) {
            val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
            botsApi.registerBot(this)
        }
        else log.warn("Не задан токен или имя пользователя для бота")
    }

    override fun getBotToken(): String? {
        return optionsMgr.getTelegramToken()
    }

    override fun onUpdateReceived(p0: Update?) {
        p0?.myChatMember?.let { memberUpdate ->
            val chatId = memberUpdate.chat.id
            if (chatId < 0) {
                val sm = SendMessage.builder()
                    .chatId(chatId) //Who are we sending a message to
                    .text("Бот был добавлен в новую группу: ${memberUpdate.chat.title}\n" +
                        "id: ${memberUpdate.chat.id}")
                    .parseMode("HTML")
                    .build()
                execute(sm)
                log.info("Бот был добавлен в новую группу: ${memberUpdate.chat.title}, id группы: ${memberUpdate.chat.id}")
            }
            else {
                p0?.let {
                    log.info("Получено сообщение: ${p0.message.text}")
                }
            }
        }
    }

    fun startBot() {
        log.info("Telegram бот запущен. Имя бота: ${getBotUsername()}")
    }

    override fun getBotUsername(): String? {
        return optionsMgr.getOptions().telegramBotName
    }

    fun send(what: String?) {
        if(optionsMgr.getTelegramToken().isBlank() || optionsMgr.getOptions().telegramBotName.isBlank()) {
            return
        }
        try {
            optionsMgr.getOptions().telegramGroupId.toLongOrNull()?.let {
                val sm = SendMessage.builder()
                    .chatId(it) //Who are we sending a message to
                    .text(what!!) //Message content
                    .parseMode("HTML")
                    .build()
                execute(sm) //Actually sending the message
            }
        } catch (e: TelegramApiException) {
            log.error(e.message)
        }
    }
}