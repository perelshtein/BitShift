import { useContext } from "react";
import { AuthContext } from "@/context/AuthContext.jsx";
import { ROUTES } from "@/links";

export default function UserMenu() {
    const { loggedIn, userInfo } = useContext(AuthContext);
    return (
        loggedIn ? (
        <aside>
            {userInfo?.isAdminPanel && <a href={ROUTES.ADMIN}>Админка</a>}
            <a href={ROUTES.ACCOUNT}>Профиль</a>
            <a href={ROUTES.MY_ORDERS}>Заявки</a>
            <a href={ROUTES.MY_REVIEWS}>Отзывы</a>
            <a href={ROUTES.CASHBACK}>Кэшбэк</a>
            <a href={ROUTES.REFERRALS}>Рефералы</a>
        </aside>
        ) : null
    )
}