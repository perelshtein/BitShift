## Авторизация: ##
Для доступа к большей части функций API требуется аутентификация (вход с логином и паролем) и авторизация (проверка прав).

**Все функции разделены на 4 группы**:  
✅ Открыт для всех - доступен без аутентификации, с сайта и из админки;  
🔵 - для пользователей, которые зашли на сайт под своим логином (прошли аутентификацию);  
🟡 - админка - для пользователей, у которых есть права `isAdminPanel`;  
🔴 - для тех, у которых помимо возможности зайти в админку, есть доп.права. Доп.права задаются для ролей.

У каждого пользователя задается логин, пароль, роль. Пароль хэшируется и хранится в базе данных. Мастер-ключ
хранится в файле настроек.  
Есть права доступа для ролей. Права позволяют менять какую-либо группу данных, например, новости.

**Список прав:**
- `isAdminPanel` - заходить в админку;
- `isEditUserAndRole` - редактировать пользователей и роли;
- `isEditNews` - редактировать новости;
- `isEditCurrency` - редактировать валюты;
- `isEditOptions` - редактировать настройки;
- `isEditDirection` - редактировать направления;
- `isEditReserve` - задавать резервы;
- `isEditNotify` - менять уведомления;
- `isEditReview` - редактировать отзывы;
- `isSendReferralPayouts` - подтверждать выплату бонусов для пользователя и реферальных бонусов.

При работе с API требуется отправлять cookie, иначе доступ будет закрыт. Кроме маршрутов, которые ✅ Открыты для всех.

### `/login`
**Метод:** POST  
**Описание:** Для прохождения аутентификации и получения cookie  
**Права:** ✅ Открыт для всех  
**Параметры:**
- `username` - имя пользователя;
- `password` - пароль.

**Ответ:**
В случае успешного входа создается сессия, генерируется токен и отправляется cookie `obmenAuthToken`.

```json
{
  "type": "success",
  "message": "Приветствуем вас в панели управления.",
  "data": {
    "userId": 3,
    "roleId": 7
  },
  "action": "ADMIN"
}
```

- `userId` - id пользователя;
- `roleId` - id роли;
- `message` - приветствие;
- `action` - если нужно после входа открыть какую-либо страницу:
    - ADMIN - админка;
    - WEBSITE - сайт;
    - ORDER_STATUS - показать статус заявки. Этот код отправляется, если есть открытая заявка.

В случае ошибки получим ответ с кодом 401 или 403 и причиной ошибки, например `Неправильный логин или пароль`.

### `/checkSession`
**Метод:** GET  
**Описание:** для проверки/обновления сессии  
**Права:** ✅ открыт для всех  
**Параметры:** cookie `obmenAuthToken`  
**Ответ:**

```json
{
  "type": "success",
  "message": "Все отлично! Сессия действительна.",
  "data": null,
  "action": null
}
```

В случае ошибки получим ответ с кодом 403 с текстом `Сессия не найдена или срок действия истек`.

