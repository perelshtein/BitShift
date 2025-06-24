import { useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from "@/context/AuthContext.jsx";
import { ROUTES } from "@/links";

//т.к. в React можно вызывать navigate() только из контекста Router,
//создадим отдельный компонент для /logout,
//а запросы к API пусть хранятся в одном файле,
//так удобнее их менять
export default function Logout() {
    const { logout } = useContext(AuthContext);
    const navigate = useNavigate();

    useEffect(() => {
        const performLogout = async () => {
            await logout();
            navigate(ROUTES.WEBSITE);
        };

        performLogout();
    }, [navigate]);

    return null;
}
