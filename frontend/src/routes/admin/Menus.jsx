import { Link, NavLink, Outlet } from "react-router-dom";
import { ROUTES } from "@/links.js";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import styles from "./admin.module.css";

export default function Menus() {  
  const navigate = useNavigate();
  //не показывать ссылку Назад на этих страницах
  const showBackLink = ![ROUTES.LOGIN, ROUTES.LOGOUT].includes(location.pathname);

  const handleBackClick = (e) => {
    e.preventDefault();
    if (window.history.length > 1) {
        navigate(-1);
    } else {        
        navigate(ROUTES.WEBSITE);
    }
  };
  
  return (
    <>
      <div className={styles.sidebar}>
        <h1>
          <img src="/coin.svg" />
          Обменник
        </h1>     
        <nav>
          <ul>            
          <li>
              <SubMenu title="Валюты"
                child={[{
                  route: ROUTES.CURRENCIES,
                  name: "Валюты"
                },
                {
                  route: ROUTES.CURRENCY_FIELDS,
                  name: "Поля валют"
                }]} />
            </li>
            <li>
              <SubMenu title="Курсы"
                child={[{
                  route: ROUTES.COURSES,
                  name: "Список курсов"
                },
                // {
                //   route: ROUTES.OPTIONS_GARANTEX,
                //   name: "Garantex"
                // },
                {
                  route: ROUTES.OPTIONS_BYBIT,
                  name: "Bybit"
                },
                {
                  route: ROUTES.OPTIONS_BINANCE,
                  name: "Binance"
                },
                {
                  route: ROUTES.OPTIONS_COINMARKETCAP,
                  name: "CoinMarketCap"
                },
                {
                  route: ROUTES.OPTIONS_CBR,
                  name: "Центробанк"
                },
                {
                  route: ROUTES.OPTIONS_MEXC,
                  name: "Mexc"
                }
                ]} />
            </li>
            <li>
              <SubMenu title="Направления"
                child={[{
                  route: ROUTES.FORMULAS,
                  name: "Формулы"
                },
                {
                  route: ROUTES.ORDER_STATUS,
                  name: "Статусы заявок"
                },
                {
                  route: ROUTES.DIRECTIONS,
                  name: "Направления"
                },
                // {
                //   route: ROUTES.DIRECTION_ADD_GROUP,
                //   name: "Добавить группу"
                // }
                ]} />
            </li>
            <li>
              <NavLink to={ROUTES.ORDERS}>Заявки</NavLink>
            </li>
            <li>
              <NavLink to={ROUTES.OPTIONS}>Настройки</NavLink>
            </li>
            <li>
              <SubMenu title="Бонусы"
                child={[{
                  route: ROUTES.BONUS_USERS,
                  name: "Для пользователей"
                },
                {
                  route: ROUTES.BONUS_USER_ORDERS,
                  name: "Для пользователей (заявки)"
                },
                {
                  route: ROUTES.BONUS_REFERRALS,
                  name: "Для рефералов"
                },
                {
                  route: ROUTES.BONUS_REFERRAL_ORDERS,
                  name: "Для рефералов (заявки)"
                }
                ]} />
            </li>
            <li>
              <SubMenu title="Уведомления"
                child={[{
                  route: ROUTES.NOTIFY_ADMIN,
                  name: "Админу"
                },
                {
                  route: ROUTES.NOTIFY_USER,
                  name: "Пользователям"
                }]} />
            </li>
            <li>
              <SubMenu title="Пользователи"
                child={[{
                  route: ROUTES.USERS,
                  name: "Пользователи"
                },
                {
                  route: ROUTES.ROLES,
                  name: "Роли"
                }]} />
            </li>            
            <li>
              <SubMenu title="Отзывы"
                child={[{
                    route: ROUTES.STOP_WORDS,
                    name: "Стоп-слова"
                  },
                  {
                    route: ROUTES.REVIEWS,
                    name: "Список отзывов"
                  }]} /> 
            </li>           
            <li>
              <NavLink to={ROUTES.NEWS}>Новости</NavLink>
            </li>
          </ul>
        </nav>     
      </div>

      <div className={styles.detail}>            
          {showBackLink && (
              <>
                  <a href="#" onClick={handleBackClick}>Назад</a>
                  {"\u00A0\u00A0 | \u00A0\u00A0"}
              </>
          )}
          <a href={ROUTES.LOGOUT}>Выход</a>
          <Outlet />
      </div>
    </>
  );
  }

  function SubMenu({title, child}) {
    const [isVisible, setIsVisible] = useState(false);
    const toggleVisibility = () => {
      setIsVisible(!isVisible);
    };
  
    return (
      <>
        <div className={isVisible ? styles.show : styles.hide} onClick={toggleVisibility}>
          {title}
        </div>
        <ul className={styles.nested}>
          {child.map((elem, index) =>
            (<li key={index}><NavLink to={elem.route}>{elem.name}</NavLink></li>)
          )}
        </ul>        
      </>
    )
  }