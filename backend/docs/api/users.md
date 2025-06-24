## Пользователи: ##

### `/api/users`
**Метод:** GET  
**Описание:** Загрузка списка пользователей  
**Права:** 🟡 админка  
**Параметры:**
- `start` - с какого элемента загружать. По умолчанию - 0;
- `count` - количество элементов для загрузки, по умолчанию - 10;
- `roleId` - id роли. Например, можно запросить только пользователей с ролью Админ;
- `query` - фильтр по почте или имени, если они включают данную фразу.  

**Ответ:**
```json
{
  "type": "success",
  "message": null,
  "data": {
    "items": [
      {
        "id": 6,
        "date": "2024-14-05 12:04:23",
        "name": "admin",
        "password": "******",
        "mail": "admin@server.com",
        "roleId": 7,
        "referralId": null,
        "cashbackPercent": null,
        "cashbackType": "FROM_SUM",
        "referralPercent": null,
        "referralType": "FROM_SUM",
        "ordersCount": 2,
        "referralName": null,
        "referralMail": null
      },
      {
        "id": 11,
        "date": "2024-12-16 22:57:06",
        "name": "Данил",
        "password": "******",
        "mail": "danil_6324@ya.ru",
        "roleId": 10,
        "referralId": null,
        "cashbackPercent": null,
        "cashbackType": "FROM_SUM",
        "referralPercent": null,
        "referralType": "FROM_SUM",
        "ordersCount": 0,
        "referralName": null,
        "referralMail": null
      }
    ],
    "total": 2,
    "page": 1,
    "pageSize": 10
  },
  "action": null
}
```

**Подробности**:
- `date` - дата регистрации;
- `password` - 6 звездочек. Старый пароль нельзя посмотреть, только задать новый;
- `cashbackPercent` - процент кэшбека с каждой сделки, которую совершил пользователь;
- `cashbackType` - тип кэшбека для пользователя: `FROM_SUM` - от суммы обмена, `FROM_PROFIT` - от прибыли обменника;
- `referralPercent` - процент кэшбека с каждого обмена, который был выполнен рефералом;
- `referralType` - тип кэшбека для рефералов: `FROM_SUM` - от суммы обмена, `FROM_PROFIT` - от прибыли обменника;
- `ordersCount` - число выполненных сделок;
- `referralName` - имя реферала;
- `referralMail` - почта реферала, для каждого пользователя задается только один раз, чтобы предотвратить мошенничество с накруткой реферальных бонусов.

### `/api/users`
**Метод:** PUT (добавить) и POST (редактировать).  
**Описание:** Добавление новых или редактирование существующих пользователей  
**Права:** 🔴 админка, `isEditUserAndRole`

**Запрос:**
POST
```json
{  "id": 11,
  "date": "2024-12-16 22:57:06",
  "name": "Данила",
  "password": "******",
  "mail": "3@4.rus",
  "roleId": 10,
  "referralId": null,
  "cashbackPercent": null,
  "cashbackType": "FROM_SUM",
  "referralPercent": null,
  "referralType": "FROM_SUM",
  "ordersCount": null,
  "referralName": null,
  "referralMail": null
}
```

**Ответ:**
```json
{
  "type": "success",
  "message": "Пользователь Данила сохранен",
  "data": null,
  "action": null
}
```

### `/api/users`
**Метод:** DELETE  
**Описание:** Удаляет пользователей  
**Параметры:** `ids` - список id для удаления  
**Права:** 🔴 админка, `isEditUserAndRole`  
**Подробности:** Нельзя удалить админа (ему задана роль с правами `isAdminPanel`), если он единственный.

**Запрос:**
DELETE /api/users?ids=1

**Ответ:**
```json
{
  "type": "success",
  "message": "Удалены пользователи: Данил",
  "data": null,
  "action": null
}
```


### `/api/users/{id}`
**Метод:** GET  
**Описание:** Загружает информацию о пользователе  
**Параметры:** `id` - id пользователя  
**Права:** 🟡 админка

**Ответ:**
```json
{
  "type": "success",
  "message": null,
  "data": {
    "id": 11,
    "date": "2024-12-16 22:57:06",
    "name": "Данил",
    "password": "******",
    "mail": "3@4.rus",
    "roleId": 10,
    "referralId": null,
    "cashbackPercent": null,
    "cashbackType": "FROM_SUM",
    "referralPercent": null,
    "referralType": "FROM_SUM",
    "ordersCount": null,
    "referralName": null,
    "referralMail": null
  },
  "action": null
}
```