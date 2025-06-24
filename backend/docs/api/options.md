### Настройки:

Настройки хранятся в 2х местах: в файле и в базе данных. В файле `config.properties` хранится самое необходимое,
например имя базы данных и пароль, а также мастер-ключ, чтобы не сохранять в базе данных учетные данные от биржи
в открытом виде.

Для работы нужен MySQL 8.  
В MySQL достаточно добавить пользователя и базу данных. При запуске приложения, если таблиц нет - они будут созданы.

### Файл настроек config.properties:
- `dbAddress=localhost` адрес базы данных MySQL, обычно это `localhost`;   
- `dbName=obmen`
- `dbPass=129aprFheF4`
- `dbPort=3306` - обычно 3306;
- `dbUser=obmen`
- `host=localhost` - адрес, на котором будет работать сервер;
- `port=8080` - порт для запуска сервера;
- `timezoneOffsetMinutes=240` - часовой пояс, смещение в минутах относительно UTC; 
- `secureKey=csasdmvc29034uhcf` - мастер-ключ.

### Маршруты:
### `/api/options`
**Метод:** GET  
**Описание:** Загружает настройки  
**Права:** 🟡 админка  
**Ответ:**
```json
{
  "type": "success",
  "message": null,
  "data": {
    "sessionTimeoutMinutes": 65,
    "serverTimezoneMinutes": 240,
    "validation": {
      "serverTimezoneMinutes": "Выбран другой часовой пояс: GMT+04:00. Требуется перезапуск приложения."
    },
    "maxRequests": 120,
    "maxRequestsSerious": 20,
    "isRandomCookie": true,
    "randomCookieName": "aa44a07a-5089-4978-8eb2-324631cbdc2c",
    "randomCookieInterval": 15,
    "randomCookieAttempts": 10,
    "smtpServer": "smtp.beget.com",
    "smtpPort": 25,
    "smtpLogin": "verify@bitshift.su",
    "smtpPassword": "******",
    "isExportCourses": true,
    "isMaintenance": false,
    "telegramBotToken": "******",
    "telegramBotName": "othTestingBot",
    "telegramGroupId": "-4755854970",
    "adminEmail": "",
    "cashbackPercent": 0.5,
    "cashbackType": "FROM_SUM",
    "referralPercent": 0.5,
    "referralType": "FROM_SUM"
  },
  "action": null
}
```

**Подробности:**
- `sessionTimeoutMinute` - таймаут сессии. Эта настройка берется из файла. Если в течение этого времени не пользоваться
API, сессия будет закрыта и нужно будет заново пройти [аутентификацию](./login.md);  
- `serverTimezoneMinutes` - часовой пояс сервера, смещение в минутах относительно UTC. Добавлено для удобства, чтобы не менять
системное время через команду date; 
- `validation` - здесь может выводиться предупреждение для любого поля в формате `fieldName`: `value`;
- `maxRequestsSerious` - макс количество запросов в минуту для маршрутов `login`, `createOrder`, для одного IP;
- `maxRequests` - макс количество запросов в минуту для остальных маршрутов, для одного IP;
- `isRandomCookie` - включить защиту от атак на базе кук;
- `randomCookieName` - название cookie, генерируется случайным образом, меняется через заданный интервал;
- `randomCookieInterval` - интервал в минутах для смены названия cookie;
- `randomCookieAttempts` - сколько раз можно зайти на сайт без кук для каждого IP в течение заданного интервала без риска
получить бан;
- `smtp*` - настройки почтового сервера для отправки уведомлений о заявках;
- `isExportCourses` - включить экспорт курсов в файл course.xml для мониторингов;
- `isMaintenance` - включить режим тех.обслуживания, сайт будет недоступен, а админка будет работать;
- `telegram*` - настройки Телеграм для уведомлений о заявках. Для корректной работы надо добавить бота в группу
и сделать администратором;
- `cashback*` - настройки кэшбека по умолчанию;
- `referral*` - настройки реферальной программы по умолчанию;

В качестве IP используется заголовок `CF-Connecting-IP` для интеграции с CloudFlare, или же ip-адрес запроса.

```json
{
  "sessionTimeoutMinutes": 60,
  "serverTimezoneMinutes": 240,
  "validation": [
    {"serverTimezoneMinutes": "Требуется перезапуск приложения. Новый часовой пояс: 240мин"}
  ],
  "maxRequests": 60,
  "maxRequestsSerious": 10,
  "isRandomCookie": true,
  "randomCookieName": "random_name",
  "randomCookieInterval": 15,
  "randomCookieAttempts": 5
}
```

### `/api/options`
**Метод:** POST  
**Описание:** Сохраняет настройки  
**Права:** 🔴 админка, `isEditOptions`  
**Запрос:**
```json
{
  "sessionTimeoutMinutes": 65,
  "serverTimezoneMinutes": 210,
  "validation": {},
  "maxRequests": 120,
  "maxRequestsSerious": 20,
  "isRandomCookie": true,
  "randomCookieName": "aa44a07a-5089-4978-8eb2-324631cbdc2c",
  "randomCookieInterval": 15,
  "randomCookieAttempts": 10,
  "smtpServer": "smtp.beget.com",
  "smtpPort": 25,
  "smtpLogin": "verify@bitshift.su",
  "smtpPassword": "******",
  "isExportCourses": true,
  "isMaintenance": false,
  "telegramBotToken": "******",
  "telegramBotName": "othTestingBot",
  "telegramGroupId": "-4755854970",
  "adminEmail": "",
  "cashbackPercent": 0.5,
  "cashbackType": "FROM_SUM",
  "referralPercent": 0.5,
  "referralType": "FROM_SUM"
}
```

**Ответ:**
```json
{
  "type": "success",
  "message": "Настройки сохранены",
  "data": null,
  "action": null
}
```

### `/api/maintenance`
**Метод:** GET  
**Описание:** Проверяет, включен ли режим тех.обслуживания. Нужен, чтобы на сайте показать страницу "Ведутся технические работы"
и не раскрывать остальные настройки.  
**Права:** ✅ Открыт для всех   
**Ответ:**  
```json
{
  "type": "success",
  "message": null,
  "data": {
    "isMaintenance": false
  },
  "action": null
}
```
