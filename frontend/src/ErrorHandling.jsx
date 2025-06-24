import { toast } from 'react-toastify';

// Централизованная обработка ошибок
// с отображением всплывающих сообщ (toast),
// понятных для пользователя
class CustomError extends Error {
    constructor({ code, status, message, messageType }) {
      super(message); // This sets the default `message` property
      this.code = code;
      this.status = status;
      this.messageType = messageType;
    }
  }
  
  //ВНИМАНИЕ,
  //функция возвращает текст ответа с сервера или объект error,
  //НО не response, как в стандартном fetch()!
  export async function safeFetch(url, options) {
    try {
      const response = await fetch(url, {...options, credentials: "include"});          
      let answerJson = await response.json();

      // Успешно
      if (response.ok) { //http code 2xx
        // if(answerJson.message) {
        //   return {...answerJson, messageType: answerJson.type == "warning" ? "warning" : "info"};
        // }
        // else return answerJson;
        return answerJson;
      }

      // Надо что-то исправить в запросе, вернем данные.
      // Но это не ошибка.
      if (answerJson.type == "warning") {
        if(answerJson.message) {
          return {...answerJson, messageType: "warning"};
        }
        return answerJson;
      }

      // А теперь - уж точно ошибка!
      throw new CustomError({
        code: response.status,
        status: response.statusText,
        message: answerJson.message,
        messageType: answerJson.type || 'error'
      });
    } catch (error) {
      if (error instanceof TypeError) {
        handleError(new CustomError({
          code: error.code,
          status: error.message,
          message: "Невозможно подключиться к серверу.",
          messageType: "error"
        }));
      }
      else {
        handleError(error);
      }
      throw error; // это не трогать, иначе ошибки не будут ловиться при вызове safeFetch()
    }
  }
  
  export function handleError(error) {
    if (error instanceof CustomError) {
      console.error(`Код ошибки: ${error.code}, Текст: ${error.status}`);
      if(error.messageType == "warning") toast.warning(error.message);
      else toast.error(error.message);
    }  
    else {
      const errorMessage = error?.message || "Неизвестная ошибка";
      toast.error(errorMessage);
      console.error(error);    
    }  
  }