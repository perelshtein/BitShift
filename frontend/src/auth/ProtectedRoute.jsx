import { Navigate } from 'react-router-dom';
import { useContext } from 'react';
import { AuthContext } from "../context/AuthContext";
import { ROUTES } from '../links';

// При доступе к страницам админки или личного кабинета
// открываем окно входа
export function ProtectedRoute({ children }) {
  const { loggedIn } = useContext(AuthContext);

  // проверка сессии или обновл страницы
  if (loggedIn === null) {
    return <div>Проверка сессии...</div>;
  }

  if (!loggedIn) {        
    return <Navigate to={ROUTES.LOGIN} replace />;
  }

  return children;
}