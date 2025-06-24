package com.github.perelshtein

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.TimeZone

// настройки, которые нужны сразу же при запуске
// они хранятся в файле
class Options {
    val properties = Properties()

    val host: String
    val port: Int
    val dbAddress: String
    val dbPort: String
    val dbName: String
    val dbUser: String
    val dbPass: String
    var timezoneOffsetMinutes: Int // смещение от GMT в минутах. если не указано - берем системный часовой пояс
    val secureKey: String
    val keyStorePassword: String
    val keyStoreAlias: String

    init {
        if(COOKIE_PRODUCTION) {
            val homeDir = System.getProperty("user.home")
            val configFile = File("$homeDir/config.properties")
            properties.load(FileInputStream(configFile))
        }
        else properties.load(FileInputStream("config.properties"))
        host = properties.getProperty("host")
            ?: throw IllegalStateException("Адрес сервера (host) не указан в config.properties")
        port = properties.getProperty("port")?.toInt()
            ?: throw IllegalStateException("Порт сервера (port) не указан в config.properties")
        dbAddress = properties.getProperty("dbAddress")
            ?: throw IllegalStateException("Адрес сервера базы данных (dbAddress) не указан в config.properties")
        dbPort = properties.getProperty("dbPort")
            ?: throw IllegalStateException("Порт базы данных (dbPort) не указан в config.properties")
        dbName = properties.getProperty("dbName")
            ?: throw IllegalStateException("Имя базы данных (dbName) не указано в config.properties")
        dbUser = properties.getProperty("dbUser")
            ?: throw IllegalStateException("Имя пользователя базы данных (dbUser) не указано в config.properties")
        dbPass = properties.getProperty("dbPass")
            ?: throw IllegalStateException("Пароль базы данных (dbPass) не указан в config.properties")
        secureKey = properties.getProperty("secureKey")
            ?: throw IllegalStateException("Мастер-ключ для шифрования (secureKey) не указан в config.properties")
        if(COOKIE_PRODUCTION) {
            keyStorePassword = properties.getProperty("keyStorePassword")
                ?: throw IllegalStateException("Пароль для хранилища SSL (keyStorePassword) не указан в config.properties")
            keyStoreAlias = properties.getProperty("keyStoreAlias")
                ?: throw IllegalStateException("Алиас для хранилища SSL (keyStoreAlias) не указан в config.properties")
        }
        else {
            keyStorePassword = ""
            keyStoreAlias = ""
        }

        timezoneOffsetMinutes = properties.getProperty("timezoneOffsetMinutes")?.toInt()
            ?: TimeZone.getDefault().getRawOffset() / (60 * 1000)
    }

    fun flush() {
        properties.setProperty("timezoneOffsetMinutes", timezoneOffsetMinutes.toString())
        properties.store(FileOutputStream("config.properties"), null)
    }
}