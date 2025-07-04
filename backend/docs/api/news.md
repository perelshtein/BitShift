## Новости:
На главной странице сайта обычно подключают новости. По сути - это те же страницы. Для удобства они выделены 
в отдельную сущность.

### `/api/news`
**Метод:** GET  
**Описание:** Запрос списка новостей.  
**Параметры:**  
- `start` - с какого элемента загружать результаты. По умолчанию - 0;  
- `count` - сколько элементов загружать за один раз. По умолчанию - 10;
- `textSize` - загрузить текст, первые N символов. По умолчанию - 0.
 
**Права:** ✅ Открыт для всех  

**Ответ:**
```json
{
  "type": "success",
  "message": null,
  "data": {
    "items": [
      {
        "id": 5,
        "date": "2024-12-25 21:28:03",
        "caption": "Россия разрабатывает централизованную платформу мониторинга для борьбы с незаконной финансовой деятельностью",
        "text": "По данным Odaily, Центральный банк России сотрудничает с агентством финансового мониторинга Росфинмониторинг с целью разработки централизованной..."
      },
      {
        "id": 4,
        "date": "2024-12-25 20:58:45",
        "caption": "Ключевые тенденции, за которыми стоит следить в 2025 году: блокчейн и цифровые инновации",
        "text": "По данным Odaily, Forbes выделил пять важных тенденций, за которыми стоит следить в 2025 году. К ним относятся регенеративное финансирование (ReFi) и..."
      },
      {
        "id": 3,
        "date": "2024-12-25 20:57:36",
        "caption": "Майкл Сэйлор рекомендует биткоин как идеальный рождественский подарок",
        "text": "Как сообщает Odaily, основатель MicroStrategy Майкл Сэйлор недавно высказался на платформе X, что нет лучшего рождественского подарка, чем биткоин..."
      }
    ],
    "total": 3,
    "page": 1,
    "pageSize": 10
  },
  "action": null
}
```

**Подробности:**  
`date` - дата создания новости. Если отредактировать новость, дата остается неизменной.

### `/api/news/{id}`  
**Метод:** GET  
**Описание:** Загрузка новости по id.  
**Параметры:** `id` - id новости.  
**Права:** ✅ Открыт для всех  
**Ответ:**  
```json
{
  "type": "success",
  "message": null,
  "data": {
    "id": 5,
    "date": "2024-12-25 21:28:03",
    "caption": "Россия разрабатывает централизованную платформу мониторинга для борьбы с незаконной финансовой деятельностью",
    "text": "По данным Odaily, Центральный банк России сотрудничает с агентством финансового мониторинга Росфинмониторинг с целью разработки централизованной платформы мониторинга, направленной на выявление и предотвращение незаконной финансовой деятельности, включая внебиржевые (OTC) криптовалютные транзакции. Эта платформа будет интегрировать информацию о ненормальных транзакциях, используя анализ данных ИИ и мониторинг в реальном времени для упреждающего выявления счетов с высоким уровнем риска. Данные будут передаваться банкам для снижения рисков.\n\nВ 2023 году объем незаконных транзакций через личные счета в России достиг 44,9 млрд рублей (около 491 млн долларов США). Эту инициативу поддерживают такие крупные банки, как Сбер и ВТБ. Платформа также будет включать механизм разрешения споров для защиты прав законных пользователей. Однако на данный момент конкретная дата запуска платформы не определена."
  },
  "action": null
}
```

### `/api/news`
**Метод:** PUT и POST  
**Описание:** Добавление или редактирование новости. Поддерживаются HTML-теги. Можно также вставить ссылку на картинку,
если закачать картинку в папку /public.  
**Права:** 🔴 админка + `isEditNews`.  
**Запрос:**
POST /api/news

```json
{
  "id": 3,
  "date": "2024-12-25 20:57:36",
  "caption": "Майкл Сэйлор рекомендует биткоин как идеальный рождественский подарок",
  "text": "Как сообщает Odaily, основатель MicroStrategy Майкл Сэйлор недавно высказался на платформе X, что нет лучшего рождественского подарка, чем биткоин (BTC). <img src=\"/public/coin.svg\" />"
}
```
**Параметры:**  
`id` - id новости для редактирования. При создании новости id=0.

**Ответ:**
```json
{
  "type": "success",
  "message": "Новость Майкл Сэйлор рекомендует биткоин как идеальный рождественский подарок обновлена",
  "data": null,
  "action": null
}
```

### `/api/news`
**Метод:** DELETE  
**Описание**: Удаление новостей по id   
**Параметры:** `ids` - список новостей  
**Права:** 🔴 админка + `isEditNews`.

**Ответ:**
```json
{
  "type": "success",
  "message": "Новость Майкл Сэйлор рекомендует биткоин как идеальный рождественский подарок удалена", 
  "data": null,
  "action": null
}
