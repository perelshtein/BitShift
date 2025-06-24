BitShift
Фронт на React/JS - админка, сайт с кабинетом пользователя.

Для сборки prod - версии:
1) в файле .env.production задать адрес backend-сервера (VITE_SERVER_ROOT);
Также можно указать:
VITE_GA_ID - счетчик Google Analytics;
VITE_TAWK_ID - ключ чата Tawk.to;
2) npm run build;
3) файлы из папки dist кинуть в папку проекта backend src/main/resources/react;
4) в проекте backend собрать общий пакет (shadowJar);
5) java -jar obmen.jar.

Для запуска dev-версии:
1) запустить backend-сервер;
2) npm run dev.