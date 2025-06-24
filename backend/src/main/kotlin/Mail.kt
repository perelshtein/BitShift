package com.github.perelshtein

import com.github.perelshtein.database.NotifyBindManager
import com.github.perelshtein.database.VerifyLinkManager
import org.koin.core.component.KoinComponent
import jakarta.mail.*
import jakarta.mail.internet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import org.koin.core.qualifier.named

class Mail: KoinComponent {
    private val log = LoggerFactory.getLogger("Mail")
    private val options: OptionsManager by inject()
    val notifyUserManager: NotifyBindManager by inject(named("user"))
    private val opt = options.getOptions()
    private val lastMailSent = mutableMapOf<String, LocalDateTime>()

    suspend fun sendVerificationEmail(email: String) {
        val verifyLinkManager: VerifyLinkManager by inject()
        val now = LocalDateTime.now()
        lastMailSent.entries.removeIf { it.value.isBefore(now.minusMinutes(1)) }
        val lastSent = lastMailSent[email]
        if (lastSent != null && lastSent.isAfter(now.minusMinutes(1))) {
            throw ValidationError("На ваш ящик $email уже отправлена ссылка. Запросить ее повторно можно через минуту.")
        }
        lastMailSent[email] = now

        val token = verifyLinkManager.add(email)
        val notify = notifyUserManager.getStatus("newUser") ?: throw ValidationError("Не задан текст для подтверждения регистрации")
        if(notify.text.isBlank()) throw ValidationError("Не задан текст для подтверждения регистрации")
        val addr = if(COOKIE_PRODUCTION) "https://${options.getHost()}" else "http://${options.getHost()}:${options.getPort()}"
        val body = notify.text.replaceTags(mapOf("register" to "${addr}/verify?token=$token"))
        send(email, notify.subject, body)
    }

    suspend fun sendGreetingsEmail(email: String, password: String) {
        val notify = notifyUserManager.getStatus("registrationSuccess") ?: throw ValidationError("Не задан текст " +
            "приветствия для нового пользователя")
        if(notify.text.isBlank()) throw ValidationError("Не задан текст приветствия для нового пользователя")
        val body = notify.text
            .replaceTags(mapOf("password" to password))
            .replaceTags(mapOf("login" to email))
        send(email, notify.subject, body)
    }

    suspend fun send(email: String, subject: String, body: String) {
        val smtpServer = opt.smtpServer
        val smtpPort = opt.smtpPort
        val smtpLogin = opt.smtpLogin
        val smtpPassword = options.getSmtpPassword()
        if(smtpServer.isBlank() || smtpPort !in 1..65535 || smtpLogin.isBlank() || smtpPassword.isBlank()) {
            log.error("Не заданы настройки SMTP")
            throw ValidationError("Не заданы настройки SMTP")
        }

        // Email setup
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", smtpServer)
            put("mail.smtp.port", smtpPort)
        }
        val session = jakarta.mail.Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(smtpLogin, smtpPassword)
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(smtpLogin))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
            this.subject = subject
            setContent(body, "text/html; charset=utf-8")
        }

        // Send async
        withContext(Dispatchers.IO) {
            Transport.send(message)
        }
    }
}