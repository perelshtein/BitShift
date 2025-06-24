import { toast } from "react-toastify";
import { useState, useContext, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from "@/context/AuthContext.jsx";
import { ROUTES } from '@/links';
import { useLocation } from "react-router-dom";

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const { login } = useContext(AuthContext);
  const { state } = useLocation();  
  const navigate = useNavigate();

  const handleLogin = async (e) => {
    e.preventDefault();
    let {isLoggedIn, action} = await login(username, password);    
    if (isLoggedIn) {
      navigate(ROUTES[action]);
    }
  };

  useEffect(() => {  
    if (state?.messageType === 'warning') {
      toast.warn(state?.message, { autoClose: false });
    } else if (state?.messageType === 'info') {
      toast.info(state?.message);
    }
    if(state?.login) setUsername(state?.login);
  }, [state]);
  
  return (    
    <form onSubmit={handleLogin} className="formLogin">    
      {state?.messageType === 'warning' && (
        <div className="warning">
          {state?.message}
        </div>
      )}
      <h2>Вход в систему</h2>
      <input
        placeholder="E-mail"
        value={username}
        onChange={(e) => setUsername(e.target.value)}
      />
      <input
        type="password"
        placeholder="Пароль"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      <button type="submit">Вход</button>
    </form>
  );
}
