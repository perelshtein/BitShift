import { createContext, useState, useEffect } from 'react';
import { safeFetch } from "../ErrorHandling";
const SERVER_ROOT = import.meta.env.VITE_SERVER_ROOT;
import { toast } from 'react-toastify';

export const AuthContext = createContext();

export function AuthProvider({ children }) {
  const [isLoggedIn, setIsLoggedIn] = useState(null); //обязательно null, иначе при обновл страницы будет выкидывать на /login, даже при рабочем cookie
  const [userInfo, setUserInfo] = useState(null);
  // const [isMaintenance, setIsMaintenance] = useState(false);

  // при загрузке приложения или смене страницы
  // проверяем, активна ли еще сессия на сервере
  // загрузим флаг "Режим тех обслуживания"
  async function validateSession() {      
    try {
      const response = await fetch(`${SERVER_ROOT}/checkSession`, {
        method: 'GET',
        credentials: 'include',
      });                
      if (!response.ok) {
          setIsLoggedIn(false);
          setUserInfo(null);
          return;        
      }

      setIsLoggedIn(true);
      // setIsMaintenance(responseJson.isMaintenance);      

      // если сессия активна, получим инф о пользователе
      if(!userInfo) {          
        const {data} = await safeFetch(`${SERVER_ROOT}/api/user`, {
          method: 'GET',
          credentials: 'include', // прикрепим куку
        });                
        setUserInfo(data);  
      }
    } catch (error) {
      console.error(error);
      setIsLoggedIn(false);
      setUserInfo(null);
    }
  }

  useEffect(() => {    
    validateSession();  
  }, [location, isLoggedIn]);

  const login = async (username, password) => {
    try {     
      const answer = await safeFetch(`${SERVER_ROOT}/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({username, password}),
      });

      // получили куку от сервера. она автоматич сохранится в броузере
      
      // сохраним id роли, под которой вошли, в local storage,
      // потому что через api его потом отдельно не выдернуть.
      // Закрыли броузер, а сессия еще живая. где взять id?
      localStorage.setItem('activeRoleId', answer.data.roleId);      
      setIsLoggedIn(true);
      return { isLoggedIn: true, action: answer.action }

    } catch(error) {
      //console.log(`login(): ${error.code} ${error.message}`)
      return { isLoggedIn: false }
    }
  };

  const logout = async() => {
    // удалим токен из кук
    document.cookie = `authToken=; Max-Age=0; Path=/`;

    //удалим ID с диска
    localStorage.removeItem('activeRoleId');

    setIsLoggedIn(false);
    setUserInfo(null);
    const { message, type } = await safeFetch(`${SERVER_ROOT}/api/logout`, {
      method: 'GET'    
    })
    if(type == "success") {
      toast.info(message);
      console.log(message);
    }    
  };
  
  return (
    <AuthContext.Provider value={{
      login, logout, loggedIn: isLoggedIn, setLoggedIn: setIsLoggedIn, userInfo, setUserInfo//, isMaintenance 
    }}>
      {children}
    </AuthContext.Provider>
  );
}