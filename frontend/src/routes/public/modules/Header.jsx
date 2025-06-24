import { ROUTES } from "@/links";
import { useContext } from "react";
import { AuthContext } from "@/context/AuthContext.jsx";
import UserMenu from "@/routes/public/modules/UserMenu";

export default function Header({styles}) {
  const { loggedIn, userInfo } = useContext(AuthContext);
  
    return (       
      <header>               
        <div>
          <div className={styles.container}>
            <a href={ROUTES.WEBSITE} className={styles.logo}>
              <img src="/coin.svg" alt="логотип-монета" />
              <img src="/logo.svg" alt="BitShift" />
            </a>

            {!loggedIn && (
              <a href={ROUTES.LOGIN} className={styles.login}>
                <img src="/icon-login.svg" />
                Войти
              </a>
            )}
            {loggedIn && (
              <div>                                
                <a href={ROUTES.ACCOUNT} className={styles.user}>
                <img src="/icon-user.svg" />
                  {userInfo?.name || ""}
                </a>
                <a href={ROUTES.LOGOUT} className={styles.logout}>
                <img src="/icon-logout.svg" />
                  Выход
                </a>
              </div>
            )
            }         
          </div>   
        </div>

        <div>
          <div className={styles.menu}>
              <a href="/">Главная</a>
              <a href="/rules">Правила</a>
              <a href="/news">Новости</a>
              <a href="/reviews">Отзывы</a>
              <a href="/contacts">Контакты</a>
          </div>
        </div>
        
        <UserMenu />

      </header>      
    );
  }