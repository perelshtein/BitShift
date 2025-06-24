import { toast } from "react-toastify";
import { useContext, useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { CommonAPIContext } from "@/context/CommonAPIContext";
import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";
import ComboList from "../components/ComboList";
import styles from "./public.module.css"; // стили обраб в WebPack
import { ROUTES } from "@/links";
import Header from "./modules/Header";
import Footer from "./modules/Footer";
import { loadData, updatePrice, updateReserveAndLimits, loadDirectionsGive, loadDirectionsGet, checkValues } from "./modules/exchangeUtils";
import StarRating from "../components/StarRating/StarRating";

export default function Index() {
  const [loading, setLoading] = useState(true);
  const hasClaimed = useRef(false);
  const { fetchCurrencies, fetchDirections, fetchNews } = useContext(CommonAPIContext);
  const { fetchUserReserve, fetchUserDirection, claimTempOrder, fetchPublicReviews, sendRefId } = useContext(WebsiteAPIContext);
  const [currenciesGive, setCurrenciesGive] = useState([]);
  const [currenciesGet, setCurrenciesGet] = useState([]);
  const [giveAmount, setGiveAmount] = useState();
  const [getAmount, setGetAmount] = useState();
  const [limitsGiveWarn, setLimitsGiveWarn] = useState();
  const [limitsGetWarn, setLimitsGetWarn] = useState();

  const fetchActiveDirections = async (...args) => {
    // Merge { status: "active" } with any existing parameters
    const params = args[0] ? { ...args[0], status: "active" } : { status: "active" };
    return await fetchDirections(params, ...args.slice(1));
  };

  //здесь храним объект валюты (id и название)
  const [giveSelected, setGiveSelected] = useState(null);
  const [getSelected, setGetSelected] = useState(null);
  const [directionId, setDirectionId] = useState();
  const [limit, setLimit] = useState(null);
  const [price, setPrice] = useState();
  const [formattedPrice, setFormattedPrice] = useState();
  const [reserve, setReserve] = useState();
  const [news, setNews] = useState([]);
  const [reviews, setReviews] = useState([]);
  const navigate = useNavigate();

  // Проверим временную заявку
  const checkTempOrder = async() => {
    let id = localStorage.getItem('tempOrderId');
    //запускаем только один раз (react в dev-режиме срабатывает два раза)
    if(id && id != "null" && !hasClaimed.current) {
      try {
        hasClaimed.current = true;
        setLoading(true);
        let answer = await claimTempOrder({id});
        //Если ошибок не было, открыть страницу с активной заявкой
        navigate(`${ROUTES.ORDER_BY_ID}/${answer.data.id}`, {
          state: { message: answer.message, messageType: answer.messageType },
        });
      }
      finally {
        localStorage.removeItem('tempOrderId');        
        setLoading(false);
      }
    }
  }

  //

  //Загрузка валют и направлений
  const loadCurAndDir = async () => {
      setGiveAmount("");
      setGetAmount("");
      const { giveList, giveSelected: newGiveSelected, getList, getSelected: newGetSelected, directionId: newDirectionId, price: newPrice } =
        await loadData(fetchCurrencies, fetchActiveDirections, getSelected?.id, giveSelected?.id);
      setCurrenciesGive(giveList);
      setGiveSelected(newGiveSelected);
      setCurrenciesGet(getList);
      // console.log(getList)
      setGetSelected(newGetSelected);      
      setDirectionId(newDirectionId);
      setPrice(newPrice);
      await updatePriceAndReserve(newPrice, newGiveSelected, newGetSelected, newDirectionId);
  }

  const loadNewsAndReviews = async () => {
    let [freshNews, freshReviews] = await Promise.all([
      fetchNews({
          count: 2,
          textSize: 200
      }).then(result => result.data.items),
      fetchPublicReviews({          
          count: 2,
          textSize: 200
      }).then(result => result.data.items)
    ]);
    setNews(freshNews);
    setReviews(freshReviews);
  }

  const initUI = async () => {    
    try {      
      setLoading(true);            
      loadCurAndDir();
      loadNewsAndReviews();
    }
    finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const rid = urlParams.get('rid');
    if(rid) sendRefId({rid: rid});
    checkTempOrder();
    initUI();
  }, []);

  // Форматирование цены, загрузка резерва  
  const updatePriceAndReserve = async (latestPrice, giveSelected, getSelected, directionId) => {
    setGiveAmount("");
    setGetAmount("");
    const formatted = updatePrice(latestPrice, giveSelected, getSelected);
    if (formatted) setFormattedPrice(formatted);

    const { reserve: newReserve, limit: newLimit } = await updateReserveAndLimits(
      directionId, getSelected, fetchUserReserve, fetchUserDirection
    );
    setReserve(parseFloat(newReserve).toFixed(giveSelected.fidelity).replace(/\.0+$/, ""));
    setLimit(newLimit);
    checkValues(newLimit, newReserve, giveAmount, getAmount, giveSelected, getSelected, setLimitsGiveWarn, setLimitsGetWarn);
  };

  useEffect(() => {    
    checkValues(limit, reserve, giveAmount, getAmount, giveSelected, getSelected, setLimitsGiveWarn, setLimitsGetWarn);
  }, [giveAmount, getAmount]);


  // Загрузка направлений Отдаю
  const handleLoadDirectionsGive = async (e) => {
    setLoading(true);
    try {      
      const { giveList, giveSelected: newGiveSelected, getSelected: newGetSelected, directionId: newDirectionId, price: newPrice } =
        await loadDirectionsGive(e, currenciesGet, fetchActiveDirections, giveSelected);
      setCurrenciesGive(giveList);    
      setGiveSelected(newGiveSelected);
      setGetSelected(newGetSelected);
      setDirectionId(newDirectionId);
      setPrice(newPrice);
      // Call update directly with all required params
      await updatePriceAndReserve(newPrice, newGiveSelected, newGetSelected, newDirectionId);
    } finally {
      setLoading(false);
    }
  };

  // Загрузка направлений Получаю
  const handleLoadDirectionsGet = async (e) => {
    setLoading(true);
    try {
      const { getList, giveSelected: newGiveSelected, getSelected: newGetSelected, directionId: newDirectionId, price: newPrice } =
        await loadDirectionsGet(e, currenciesGive, fetchActiveDirections, getSelected);        
      setCurrenciesGet(getList);
      setGiveSelected(newGiveSelected);
      setGetSelected(newGetSelected);
      setDirectionId(newDirectionId);
      setPrice(newPrice);
      // Call update directly with all required params
      await updatePriceAndReserve(newPrice, newGiveSelected, newGetSelected, newDirectionId);
    } finally {
      setLoading(false);
    }
  };

  //Меняем направления местами
  const handleSwap = async () => {    
    initUI();
  };

  //Нажали кнопку Обменять
  const handleOrder = () => {
    if(limit?.popup) {
      // alert(limit.popup);      
    }
    if(!giveSelected || !getSelected || !giveAmount || !getAmount) return;
    let params = new URLSearchParams();
    params.append("directionId", directionId);
    params.append('amountGet', getAmount);
    navigate(`${ROUTES.CHECKUP_ORDER}?${params.toString()}`);
  }

  const updateGetAmount = (input) => {
    const amount = input.replace(/[^0-9. ,]/g, "");    
    setGetAmount(amount);
    const giveAmount = (amount/price).toFixed(giveSelected.fidelity);
    setGiveAmount(giveAmount);
  }

  const updateGiveAmount = (input) => {
    const amount = input.replace(/[^0-9. ,]/g, "");
    setGiveAmount(amount);
    const getAmount = (amount*price).toFixed(getSelected.fidelity);
    setGetAmount(getAmount);
  }

  if (loading) {
    return (
      <div className={styles.website}>
        <Header styles={styles} />
        <div className={styles.modal}>
          <div className={styles.spinner} />
        </div>       
      </div>
    )
  }

  return (     
    <div className={styles.websiteContainer}>
      <div className={styles.website}>      
        <Header styles={styles} />

        <main>
          <div className={styles.obmenBar}>
            <div className={styles.threeCol}>
              <div>
                <h3>Отдаю</h3>
                <ComboList rows={currenciesGive} selectedId={giveSelected?.id} setSelectedId={handleLoadDirectionsGet} />
                <input className={styles.currencyAmount} placeholder={limit?.minSumGive.toFixed(giveSelected.fidelity)}
                  value={giveAmount} onChange={e => updateGiveAmount(e.target.value)} />
                {limitsGiveWarn && <div className={styles.warning}>{limitsGiveWarn}</div>}
              </div>
              <a className={styles.changeDirections} onClick={handleSwap}>
                <img src="/change-directions.svg" alt="Поменять направления местами" />
              </a>
              <div>
                <h3>Получаю</h3>
                <ComboList rows={currenciesGet} selectedId={getSelected?.id} setSelectedId={handleLoadDirectionsGive} />
                <input className={styles.currencyAmount} placeholder={limit?.minSumGet.toFixed(getSelected.fidelity)}
                  value={getAmount} onChange={e => updateGetAmount(e.target.value)} />
                {limitsGetWarn && <div className={styles.warning}>{limitsGetWarn}</div>}
                {formattedPrice && (
                  <p>Курс: {formattedPrice}</p>
                )}
                {reserve > 0 && (                  
                  <p>Резерв: {reserve} {getSelected?.code}</p>
                )}
              </div>
            </div>    
            <button className={styles.obmenButton} onClick={handleOrder}>Обменять</button>      
          </div>          

          {reviews && (<>
          <div className={styles.decoratedHeader}>
            <h2>Отзывы</h2>
          </div>

          <div className={styles.reviews}>
            {Array.from(reviews).map((it, index) =>
              <div key={index}>
                <h3>{it.caption}</h3>
                {new Date(it.date).toLocaleString()}
                <div style={{display: "grid", placeItems: "center"}}>
                  <StarRating value={it.rating} disabled />
                </div>
                <p>{it.text}</p>
              </div>
            )}           
          </div>
          </>)}

          {news && (<>
          <div className={styles.decoratedHeader}>
            <h2>Новости</h2>
          </div>

          <div className={styles.reviews}>
            {Array.from(news).map((it, index) =>              
              <div key={index} style={{height: "100%"}}>
                <h3>{it.caption}</h3>
                {new Date(it.date).toLocaleString()}                
                <p>{it.text}</p>
                <p className={styles.detailLink}>
                  <a href={`${ROUTES.WEBSITE_NEWS}/${it.id}`}>Подробнее...</a>
                </p>
              </div>
            )}           
          </div>
          </>)}

        </main>

        <Footer styles={styles} />
      </div>
    </div>
  );
}