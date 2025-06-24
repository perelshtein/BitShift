package com.github.perelshtein.routes

import com.github.perelshtein.AccessControl
import com.github.perelshtein.COOKIE_PRODUCTION
import com.github.perelshtein.CookieGen
import com.github.perelshtein.FRONT_PORT_DEV
import com.github.perelshtein.LOG_TYPE
import com.github.perelshtein.Mail
import com.github.perelshtein.Options
import com.github.perelshtein.SessionManager
import com.github.perelshtein.TempOrderStorage
import com.github.perelshtein.User
import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.ReferralManager
import com.github.perelshtein.database.VerifyLinkManager
import com.github.perelshtein.generateHash
import com.github.perelshtein.generateRandom
import com.github.perelshtein.withUser
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.reflect.typeInfo
import org.koin.java.KoinJavaComponent.get
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun Route.verify() {
    val log = LoggerFactory.getLogger("Route.verify")

    get("/verify") {
        runCatching {
            val verifyMgr: VerifyLinkManager = get(VerifyLinkManager::class.java)
            val mailMgr: Mail = get(Mail::class.java)
            val accessControl: AccessControl = get(AccessControl::class.java)
            val sessionManager: SessionManager = get(SessionManager::class.java)
            val cookieGen: CookieGen = get(CookieGen::class.java)
            val options = Options()

            // Проверяем ссылку подтверждения, добавляем пользователя, отправляем ему пароль
            val token = call.parameters["token"] ?: throw ValidationError("Параметр token не задан")
            val mail = verifyMgr.getEmailByToken(token) ?: throw ValidationError("Ссылка недействительна. Попробуйте ещё раз")
            val password = String().generateRandom()
            val role = accessControl.getRoleByName("User") ?: throw ValidationError("Роль User не найдена")
            accessControl.addUser(User{
                this.name = mail
                this.password = password.generateHash()
                this.mail = mail
                this.roleId = role.id
            })
            mailMgr.sendGreetingsEmail(mail, password)
            verifyMgr.deleteToken(token)

            // Создаем cookie для аутентификации пользователя на сайте
            val user = accessControl.getUserByMail(mail) ?: throw ValidationError("Пользователь ${mail} не найден")
            val sessionToken = sessionManager.createSession(user.id)
            cookieGen.generate(call, sessionToken)
            log.info("Пользователь ${mail} успешно зарегистрирован.")

            // отправляем пользователя на главную страницу в react router
            // и даем с собой печенье (cookie)
            val addr = if(COOKIE_PRODUCTION) "https://${options.host}/" else "http://${options.host}:${FRONT_PORT_DEV}/"
            call.respondRedirect(addr)

        }.getOrElse { exception ->
            log.withUser(call, exception.message ?: "Неизвестная ошибка", LOG_TYPE.ERROR)
            throw exception
        }
    }
}