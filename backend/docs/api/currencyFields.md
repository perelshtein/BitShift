## Поля валют: ##

**Описание:**
Поля валют позволяют указывать доп.информацию при обменах. Например, для валюты TON требуется доп.поле Тег назначения (Memo).

### `/api/currencyFields`
**Метод:** GET  
**Описание**: Загружает все доп.поля без привязки к валютам.  
**Права:** ✅ Открыт для всех  
**Ответ:**  
```json
{
  "type": "success",
  "message": null,
  "data": [
    {
      "id": 1,
      "name": "ФИО",
      "isRequired": true,
      "hintAccountFrom": "Ваши документы?",
      "hintAccountTo": "Усы и хвост!"
    },
    {
      "id": 6,
      "name": "MEMO",
      "isRequired": false,
      "hintAccountFrom": "",
      "hintAccountTo": ""
    }
  ],
  "action": null
}
```

**Подробности:**
- `hintAccountFrom` - подсказка для валюты Отдаю;  
- `hintAccountTo` - подсказка для валюты Получаю;  
- `isRequired` - `true` или `false`. Если включено - это поле будет обязательным для заполнения.  

### `/api/currencyFields`
**Метод:** DELETE  
**Описание**: Удаляет доп.поля по id.  
**Параметры:** `ids` - список id для удаления.  
**Права:** 🔴 админка + `isEditCurrency`  
**Ответ:**
```json
{
  "type": "success",
  "message": "Поле Memo удалено",
  "data": null,
  "action": null
}
```

### `/api/currencyField`
**Метод:** PUT или POST  
**Описание**: Добавляет или изменяет доп.поле  
**Права:** 🔴 админка + `isEditCurrency`  
**Запрос:**  
```json
{
  "name": "ФИО",
  "isRequired": false,
  "hintAccountFrom": "Ваши документы?",
  "hintAccountTo": "Уши",
  "id": 1
}
```

**Подробности:**  
`name` - обязательное поле;  
`id` - заполняется только при редактировании (POST).

**Ответ:**
```json
{
  "type": "success",
  "message": "Поле ФИО обновлено",
  "data": {
    "id": 1
  },
  "action": null
}
```

`id` в ответе используется для обновления контекста на фронте в React. Чтобы не отправлять еще один запрос и не загружать
список всех полей.

### `/api/currencyField/{id}/currencies`
**Метод:** GET  
**Описание:** Загрузка списка валют для поля  
**Параметры:** `id` - id поля  
**Права:** 🟡 админка  
**Запрос:**
GET /api/currencyField/6/currencies  
**Ответ:**
```json
{
  "type": "success",
  "message": null,
  "data": {
    "give": [2],
    "get": [1, 3]
  },
  "action": null
}
```
**Подробности**:  
`give: [2]` - поле будет отображаться для валюты Отдаю с id=2;  
`get: [1,3]` - поле будет отображаться для валют Получаю с id=1 и id=3.

### `/api/currencyField/{id}/currencies`
**Метод:** POST  
**Описание:** Привязка валют для поля  
**Права:** 🔴 админка + `isEditCurrency`  
**Параметры:** `id` - id поля  
**Запрос:**  
```json
{
  "give": [2,1,5],
  "get": [2,1]
}
```
**Ответ:**  
```json
{
  "type": "success",
  "message": "Валюты для поля ФИО заданы успешно.",
  "data": null,
  "action": null
}
```

### `/api/currency/{id}/fields`
**Метод:** GET  
**Описание:** Загрузка полей Отдаю и Получаю для валюты. Маршрут нужен при создании заявки, чтобы отображать доп.поля.  
**Параметры:** `id` - id валюты  
**Права:** ✅ Открыт для всех  
**Запрос:**
GET /api/currency/1/fields  
**Ответ:**
```json
{
  "type": "success",
  "message": null,
  "data": {
    "give": [1],
    "get": [1,2]
  },
  "action": null
}
```
**Подробности:** `[1]` и `[1,2]` - это id полей Получаю и Отдаю.

### `/api/currency/{id}/fields`
**Метод:** POST  
**Описание:** Установка полей Отдаю и Получаю для валюты.  
**Параметры:** `id` - id валюты  
**Права:** 🔴 админка + `isEditCurrency`  
**Запрос:**
POST /api/currency/1/fields

```json
{
  "give":[1,5],
  "get":[1]
}
```
**Ответ:**  
```json
{
  "type": "success",
  "message": "Доп.поля для валюты RUB заданы успешно.",
  "data": null,
  "action": null
}
```

