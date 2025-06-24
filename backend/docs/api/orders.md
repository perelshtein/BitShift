## Заявки:
Это запись о проведенном (или отмененном) обмене. Каждая заявка содержит как минимум: дату, имя пользователя,
объем валюты, счет Отдаю, счет Получаю, прибыль обменного пункта.    

---

## **🔹 API для админки**

### `/api/orders`
**Метод:** GET;  
**Описание:** Загрузка списка заявок;  
**Права:** 🟡 - админка;  
**Параметры (их указывать не обязательно):**   
- `start` - с какого элемента загружать результаты. По умолчанию - `0`;
- `count` - количество элементов для загрузки за один раз. По умолчанию - `100`;
- `status` - искать только заявки с [определенным статусом]("./statuses.md");
- `filter` - фильтр по полям: Имя пользователя, Почта, Реквизиты, Счет отдаю, Счет получаю. Будут загружены только те
заявки, которые в любом из этих полей будут содержать текст, который задан в `filter`;
- `userId` - id пользователя, если нужно загрузить заявки только для него;
- `fromId` - id валюты Отдаю;
- `toId` - id валюты Получаю;
- `dateStart` - выдаст все заявки после выбранной даты;
- `dateEnd` - выдаст все заявки до выбранной даты.

**Запрос:**
GET api/orders?start=0&count=10&dateStart=2025-04-17T00:00

**Ответ**:
```json
{
  "type": "success",
  "message": null,
  "data": {
    "items": [
      {
        "id": 267,
        "userId": 3,
        "userName": "admin",
        "userMail": "dev@null.coca",
        "from": {
          "id": 1,
          "name": "Сбер RUB",
          "code": "RUB",
          "amount": 886.729
        },
        "to": {
          "id": 6,
          "name": "USDT BEP-20",
          "code": "USDT",
          "amount": 10
        },
        "dateCreated": "2025-05-23 22:48:38",
        "dateUpdated": "2025-05-23 22:51:15",
        "walletFrom": "",
        "walletTo": "0xd2404FA0EEB8c876eC8dE9BA92d5bF8c436B4177",
        "requisites": "",
        "profit": 0.43,
        "status": "completed",
        "isActive": false,
        "course": 88.67,
        "fieldsGive": {
          "ФИО": "4144"
        },
        "fieldsGet": {},
        "isManualGive": true,
        "isManualGet": false,
        "rateGive": 0.011764705882352941,
        "rateGet": 1,
        "statusHistory": [
          {
            "date": "2025-05-23 22:48:37",
            "src": "user",
            "status": "new"
          },
          {
            "date": "2025-05-23 22:48:50",
            "src": "admin_panel",
            "status": "payed"
          },
          {
            "date": "2025-05-23 22:50:11",
            "src": "autopay",
            "status": "waitingForPayout"
          },
          {
            "date": "2025-05-23 22:51:13",
            "src": "autopay",
            "status": "completed"
          }
        ],
        "refId": null
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 10
  },
  "action": null
}
```

**Подробности:**
- `rateGive` - курс Отдаю;  
- `rateGet` - курс Получаю.

### `/api/order/{id}`
**Метод:** GET;  
**Описание:** Загрузка заявки по id;    
**Права:** 🟡 - админка;  
**Параметры:** 
- `id` - id заявки.

**Запрос:**
GET /api/order/267
  
**Ответ**:
```json
{
  "type": "success",
  "message": null,
  "data": {
    "id": 267,
    "userId": 3,
    "userName": "admin",
    "userMail": "dev@null.coca",
    "from": {
      "id": 1,
      "name": "Сбер RUB",
      "code": "RUB",
      "amount": 886.729
    },
    "to": {
      "id": 6,
      "name": "USDT BEP-20",
      "code": "USDT",
      "amount": 10
    },
    "dateCreated": "2025-05-23 22:48:38",
    "dateUpdated": "2025-05-23 22:51:15",
    "walletFrom": "",
    "walletTo": "0xd2404FA0EEB8c876eC8dE9BA92d5bF8c436B4177",
    "requisites": "",
    "profit": 0.43,
    "status": "completed",
    "isActive": false,
    "course": 88.67,
    "fieldsGive": {
      "ФИО": "4144"
    },
    "fieldsGet": {},
    "isManualGive": true,
    "isManualGet": false,
    "rateGive": 0.011764705882352941,
    "rateGet": 1,
    "statusHistory": [
      {
        "date": "2025-05-23 22:48:37",
        "src": "user",
        "status": "new"
      },
      {
        "date": "2025-05-23 22:48:50",
        "src": "admin_panel",
        "status": "payed"
      },
      {
        "date": "2025-05-23 22:50:11",
        "src": "autopay",
        "status": "waitingForPayout"
      },
      {
        "date": "2025-05-23 22:51:13",
        "src": "autopay",
        "status": "completed"
      }
    ],
    "refId": null
  },
  "action": null
}
```

### `/api/order`
**Метод:** POST;  
**Описание:** Редактирование существующих заявок;  
**Права:** 🔴 админка + `isEditOrders`;  
**Параметры:**
`ids` - id заявок;  

**Запрос:**
POST api/order

```json
{
  "ids": [
    "258"
  ],
  "status": "waitingForPayment",
  "isActive": false,
  "requisites": "",
  "profit": 0,
  "walletFrom": "0xbe992f08c9324f03f9f4a15379fe6daec1236ef6",
  "rateGive": 1,
  "rateGet": 0.25674
}
```

### `Удаление заявок`
Заявки не могут быть удалены, но можно поставить статус "Удаленная". Возможно, позже будет добавлена возможность
архивации заявок. Если нужно именно удалить заявку, можно это сделать через базу данных.

---

## **🔹 API для сайта**
Каждый пользователь видит только свои заявки.
### `/api/user/orders`
**Метод:** GET;  
**Описание:** Загрузка списка заявок;  
**Права**: 🔵 авторизованный пользователь;  
**Параметры (их указывать не обязательно):**
- `start` - с какого элемента загружать результаты. По умолчанию - `0`;
- `count` - количество элементов для загрузки за один раз. По умолчанию - `100`;
- `status` - искать только заявки с [определенным статусом]("./statuses.md");
- `fromId` - id валюты Отдаю;
- `toId` - id валюты Получаю;
- `dateStart` - выдаст все заявки после выбранной даты;
- `dateEnd` - выдаст все заявки до выбранной даты.

**Ответ**:
```json
{
  "type": "success",
  "message": null,
  "data": {
    "items": [
      {
        "id": 267,
        "userId": 3,
        "dateCreated": "2025-05-23 22:48:38",
        "dateUpdated": "2025-05-23 22:51:15",
        "walletFrom": "",
        "walletTo": "0xd2404FA0EEB8c876eC8dE9BA92d5bF8c436B4177",
        "requisites": "",
        "amountFrom": 886.729,
        "amountTo": 10,
        "status": "completed",
        "isActive": false,
        "fromXmlCode": "SBERRUB",
        "fromCode": "RUB",
        "fromName": "Сбер RUB",
        "toXmlCode": "USDTBEP20",
        "toCode": "USDT",
        "toName": "USDT BEP-20",
        "fieldsGive": {
          "ФИО": "4144"
        },
        "fieldsGet": {},
        "needsTxId": false,
        "dateStatusUpdated": "2025-05-23 22:51:13",
        "deleteInterval": 30
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 10
  },
  "action": null
}
```

### `/api/user/order/{id}`
**Метод:** GET;  
**Описание:** Загрузка заявки по id;    
**Права:** 🔵 авторизованный пользователь;  
**Параметры:**
- `id` - id заявки.

**Ответ**:
```json
{
  "type": "success",
  "message": null,
  "data": {
    "id": 267,
    "userId": 3,
    "dateCreated": "2025-05-23 22:48:38",
    "dateUpdated": "2025-05-23 22:51:15",
    "walletFrom": "",
    "walletTo": "0xd2404FA0EEB8c876eC8dE9BA92d5bF8c436B4177",
    "requisites": "",
    "amountFrom": 886.729,
    "amountTo": 10,
    "status": "completed",
    "isActive": false,
    "fromXmlCode": "SBERRUB",
    "fromCode": "RUB",
    "fromName": "Сбер RUB",
    "toXmlCode": "USDTBEP20",
    "toCode": "USDT",
    "toName": "USDT BEP-20",
    "fieldsGive": {
      "ФИО": "4144"
    },
    "fieldsGet": {},
    "needsTxId": false,
    "dateStatusUpdated": "2025-05-23 22:51:13",
    "deleteInterval": 30
  },
  "action": null
}
```

### `/api/user/order`
**Метод:** PUT;  
**Описание:** Создание новой заявки;  
**Права:** ✅ Открыт для всех;  
**Параметры:**
- `email` - почта пользователя (обязательно);  
- `currencyFromId` - ID валюты Отдаю (обязательно);    
- `currencyToId` - ID валюты Получаю (обязательно);  
- `wallet` - адрес кошелька Получаю (обязательно);  
- `amountFrom` > 0 или `amountTo` > 0 (один из двух) - объем валюты Отдаю или Получаю;    
- `giveFields` - массив полей Отдаю;
- `getFields` - массив полей Получаю;  
- `refId` - id реферала (он получит бонусы после завершения обмена)

**Запрос:**
```json
{
  "email": "dev@null.coca",
  "currencyFromId": 1,
  "currencyToId": 6,
  "amountFrom": "2000.50",
  "amountTo": "22.87",
  "wallet": "0xd2404FA0EEB8c876eC8dE9BA92d5bF8c436B4177",
  "giveFields": {
    "ФИО": "444"
  },
  "getFields": {}
}
```

**Ответы:**
1) Если пользователь с такой почтой еще не зарегистрирован, ему будет отправлено письмо для регистрации, и будет создана
временная заявка. По ссылке в письме нужно перейти в течении 5мин:
```json
{
  "type": "success",
  "message": "Вам отправлено письмо на $email для подтверждения регистрации. Пожалуйста, перейдите по ссылке.",
  "data": {
    "tempOrderId": "weahr291349adsf218yefhwe"
  },
  "action": null
}
```
- HTTP-код: `202 (Accepted)`.

2) Если пользователь зарегистрирован, но еще не вошел на сайт, будет создана временная заявка, которая хранится 5 мин: 
```json
{
    "type": "success",
    "message": "Для создания заявки нужно войти с логином и паролем.",
    "data": {
      "login": "dev@null.com",
      "tempOrderId": "weahr291349adsf218yefhwe"
    },
    "action": "LOGIN"    
}
```
- `action` - какую страницу надо открыть после получения ответа от сервера.    
В данном случае надо открыть `LOGIN` - окно входа;
- HTTP-код: `202 (Accepted)`.

3) Если пользователь вошел на сайт, но у него уже есть активная заявка:
```json
{
    "type": "success",
    "message": "У вас уже есть активная заявка",
    "data": {
      "id": 32
    },
    "action": "ORDER_BY_ID"    
}
```

- `action` - какую страницу надо открыть после получения ответа от сервера.    
  Открыть `ORDER_BY_ID` - страницу заявки;
- `id` - id заявки (активной, которую надо закрыть, прежде чем создавать новую);
- HTTP-код: `409 (Conflict)`.

4) Если пользователь вошел на сайт, и активных заявок у него нет, тогда создаем новую.
```json
{
  "type": "success",
  "message": "Заявка создана",
  "data": {
    "id": 268
  },
  "action": "ORDER_BY_ID"
}
```
- `id` - id новой заявки;
- HTTP-код: `201 (Created)`.

Для каждого статуса заявки есть таймаут. Если статус не меняется в течение заданного времени, заявка закрывается автоматически.
- `waitingForPayment` (ожидание оплаты): ручной прием - 15мин, авто - 30мин;  
- `waitingForConfirmation` (ожидание подтверждения) - ручной прием - 60мин, авто - 720мин / 12ч (если задана низкая комиссия btc - придется ждать несколько часов)
- `waitingForPayout` (ожидание отправки исходящего перевода) - 60;
- `payed` (оплачено) - ручной режим - 30мин, авто - 3;
- `error` - 1;
- `другие варианты` - 30.

### `/api/user/order/claim`
**Метод:** PUT;  
**Описание:** Связывает id временной заявки с id пользователя;  
**Права:** 🔵 Авторизованный пользователь;  
**Параметры:**
- `tempOrderId` - id временной заявки.

**Ответы:**
1) У пользователя уже есть активная заявка:
```json
{
  "type": "warning",
  "message": "У вас уже есть активная заявка",
  "data": {
    "id": 6
  },
  "action": "CHECKUP_ORDER"
}
```
- HTTP-код: `409 (Conflict)`.

2) У пользователя нет активных заявок, создаем новую (переводим временную заявку в постоянную):
```json
{
  "type": "success",
  "message": "Заявка успешно подтверждена",
  "data": {
    "id": 7
  },
  "action": "CHECKUP_ORDER"
}
```
- HTTP-код: `201 (Created)`.

### `/api/user/order/txid`
**Метод:** PUT;  
**Описание:** Задает txid (id транзакции) для активной заявки. При автоматическом приеме средств биржа выдает только один
номер кошелька для каждой валюты. Если одновременно создать две заявки на той же валюте, нельзя будет понять, какая заявка оплачена первой. В таких случаях 
у пользователя запрашивается id транзакции.  
**Права:** 🔵 Авторизованный пользователь;  
**Параметры:**
- `txId` - id транзакции.