## Роли: ##

### `/api/roles`
**Метод:** GET  
**Описание:** Загрузка списка ролей.  
**Права:** 🟡 админка.  
**Ответ:**
```json
{
  "type": "success",
  "message": null,
  "data": [
    {
      "id": 7,
      "name": "Admin",
      "isAdminPanel": true,
      "isEditUserAndRole": true,
      "isEditOptions": true,
      "isEditCurrency": true,
      "isEditNews": true,
      "isEditDirection": true,
      "isEditReserve": true,
      "isEditNotify": true,
      "isEditReview": true,
      "isSendReferralPayouts": false
    },
    {
      "id": 10,
      "name": "User",
      "isAdminPanel": false,
      "isEditUserAndRole": false,
      "isEditOptions": false,
      "isEditCurrency": false,
      "isEditNews": false,
      "isEditDirection": false,
      "isEditReserve": false,
      "isEditNotify": false,
      "isEditReview": false,
      "isSendReferralPayouts": false
    }
  ],
  "action": null
}
```
**Подробности**:
В каждой роли указаны права доступа, например если `isEditCurrency` = true, это позволяет редактировать валюты.  
`isAdminPanel` - можно заходить в админку и пользоваться API маршрутами, которые отмечены 🟡 желтым.

### `/api/roles`
**Метод:** PUT и POST  
**Описание:** Создание новой роли или обновление существующей.  
**Права:** 🔴 админка, `isEditUserAndRole`  
**Запрос:**
```json
{
  "name": "Admin",
  "id": 7,
  "isAdminPanel": true,
  "isEditUserAndRole": true,
  "isEditNews": true,
  "isEditOptions": true,
  "isEditCurrency": true,
  "isEditDirection": true,
  "isEditReserve": true,
  "isEditNotify": true,
  "isEditReview": true,
  "isSendReferralPayouts": true
}
```

**Ответ:**
```json
{
  "type": "success",
  "message": "Роль Admin сохранена",
  "data": {
    "id": 7
  },
  "action": null
}
```

### `/api/roles`
**Метод:** DELETE   
**Описание:** Удаление ролей  
**Параметры:** `ids` - список id ролей  
**Права:** 🔴 админка, `isEditUserAndRole`

**Запрос:**

**Ответ:**
**Запрос:**
DELETE /api/roles?ids=7

**Ответ:**
```json
{
  "type": "success",
  "message": "Роли удалены: User",
  "data": null,
  "action": null
}
```