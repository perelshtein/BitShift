import { toast } from "react-toastify";
import { useContext, useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { CommonAPIContext } from "@/context/CommonAPIContext";
import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";
import { AuthContext } from "@/context/AuthContext";
import styles from "./public.module.css";
import { ROUTES } from "@/links";
import Header from "./modules/Header";
import Footer from "./modules/Footer";
import ComboList from "../components/ComboList";
import { loadData, updatePrice, updateReserveAndLimits, loadDirectionsGive, loadDirectionsGet, checkValues } from "./modules/exchangeUtils";
import AddressChecker from "../components/AddressChecker";

export default function CheckupOrder() {
  const [loading, setLoading] = useState(true);
  const searchParams = new URLSearchParams(window.location.search);
  const navigate = useNavigate();

  const { fetchCurrencies, fetchDirections, fetchOrderStatus, getDirectionId } = useContext(CommonAPIContext);
  const { fetchUserReserve, fetchUserDirection, sendUserOrder, sendRefId } = useContext(WebsiteAPIContext);
  const { userInfo } = useContext(AuthContext);
  const [currenciesGive, setCurrenciesGive] = useState([]);
  const [currenciesGet, setCurrenciesGet] = useState([]);
  const [giveAmount, setGiveAmount] = useState();
  const [getAmount, setGetAmount] = useState(searchParams.get("amountGet"));
  const [limitsGiveWarn, setLimitsGiveWarn] = useState();
  const [limitsGetWarn, setLimitsGetWarn] = useState();
  const [status, setStatus] = useState(null);

  //здесь храним объект валюты (id и название)
  const [giveSelected, setGiveSelected] = useState(null);
  const [getSelected, setGetSelected] = useState(null);
  const [directionId, setDirectionId] = useState(searchParams.get("directionId"));
  const [giveCode, setGiveCode] = useState(searchParams.get("cur_from"));
  const [getCode, setGetCode] = useState(searchParams.get("cur_to"));
  const [limit, setLimit] = useState(null);
  const [price, setPrice] = useState();
  const [formattedPrice, setFormattedPrice] = useState();
  const [reserve, setReserve] = useState();
  const [wallet, setWallet] = useState();
  // const navigate = useNavigate();
  const [giveFields, setGiveFields] = useState([]);
  const [getFields, setGetFields] = useState([]);
  const [giveFieldValues, setGiveFieldValues] = useState();
  const [getFieldValues, setGetFieldValues] = useState();
  const [walletChecked, setWalletChecked] = useState(null);
  const [email, setEmail] = useState();

  //редакт доп.полей Отдаю
  const handleGiveFieldChange = (name, value) => {
    setGiveFieldValues(prev => ({ ...prev, [name]: value }));
  };
  
  //редакт доп.полей Получаю
  const handleGetFieldChange = (name, value) => {
    setGetFieldValues(prev => ({ ...prev, [name]: value }));
  };

  // Форматирование цены, загрузка резерва  
  const updatePriceAndReserve = async (latestPrice, giveSelected, getSelected, directionId) => {
    // setGiveAmount("");
    // setGetAmount("");
    const formatted = updatePrice(latestPrice, giveSelected, getSelected);
    if (formatted) setFormattedPrice(formatted);

    const { reserve: newReserve, limit: newLimit } = await updateReserveAndLimits(
      directionId, getSelected, fetchUserReserve, fetchUserDirection
    );
    setReserve(parseFloat(newReserve).toFixed(giveSelected.fidelity).replace(/\.0+$/, ""));
    setLimit(newLimit);
    checkValues(newLimit, newReserve, giveAmount, getAmount, giveSelected, getSelected, setLimitsGiveWarn, setLimitsGetWarn);
  };

  // Загрузка направлений Отдаю
  const handleLoadDirectionsGive = async (e) => {
    setLoading(true);
    try {
      const { giveList, giveSelected: newGiveSelected, getSelected: newGetSelected, directionId: newDirectionId, price: newPrice } =
        await loadDirectionsGive(e, currenciesGet, fetchDirections, giveSelected);
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
        await loadDirectionsGet(e, currenciesGive, fetchDirections, getSelected);      
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

  //Проверка значений и отправка заявки
  const handleSend = async () => {
    if(!giveSelected || !getSelected) {
      toast.warn("Выберите валюту Отдаю и Получаю");
      return;
    }
    if(giveAmount == 0 || getAmount == 0) {
      toast.warn("Сумма для обмена должна быть больше 0");
      return;
    }

    let checkFieldsGive = Object.fromEntries(
      giveFields
        .filter(it => it.isRequired)
        .map(it => [it.name, giveFieldValues?.[it.name] ?? null])
        .filter(([_, value]) => value == null) // Удалить поля, которые заполнены
    );    
    if(Object.keys(checkFieldsGive).length > 0) {
      toast.warn(`Нужно заполнить поля Отдаю: ${Object.keys(checkFieldsGive)}`);
      return;
    }

    let checkFieldsGet = Object.fromEntries(
      getFields
        .filter(it => it.isRequired)
        .map(it => [it.name, getFieldValues?.[it.name] ?? null])
        .filter(([_, value]) => value == null) // Удалить поля, которые заполнены
    );    
    if(Object.keys(checkFieldsGet).length > 0) {
      toast.warn(`Нужно заполнить поля Получаю: ${Object.keys(checkFieldsGet)}`);
      return;
    }

    if(email?.trim().length == 0) {
      toast.warn("Адрес почты не указан");
      return;
    }

    if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      toast.warn("Укажите адрес почты в формате a@b.com");
      return;
    }


    if(wallet?.trim().length == 0) {
      toast.warn("Адрес кошелька не указан");
      return
    }

    if(walletChecked == false) {
      toast.warn("Адрес кошелька неправильный");
      return
    }

    let result = await sendUserOrder({ body: {
      email: email,
      currencyFromId: giveSelected.id,
      currencyToId: getSelected.id,
      amountFrom: giveAmount,
      amountTo: getAmount,
      wallet: wallet,
      giveFields: giveFieldValues || {},
      getFields: getFieldValues || {}
    }});
    if(result.data.tempOrderId) localStorage.setItem('tempOrderId', result.data.tempOrderId || null);
    if(result.action == "LOGIN") {
      navigate(ROUTES.LOGIN, {
        state: { message: result.message, login: result.login },
      });
    }
    if(result.action == "ORDER_BY_ID") {
      navigate(`${ROUTES.ORDER_BY_ID}/${result.data.id}`, {
        state: { message: result.message, messageType: result.messageType },
      });
    }
    toast.info(result.message);
  }

  const updateGetAmount = (input) => {
    const amount = input.replace(/[^0-9. ,]/g, "");
    setGetAmount(amount);
    const giveAmount = (amount / price).toFixed(giveSelected.fidelity);
    setGiveAmount(giveAmount);
  }

  const updateGiveAmount = (input) => {
    const amount = input.replace(/[^0-9. ,]/g, "");
    setGiveAmount(amount);
    const getAmount = (amount * price).toFixed(getSelected.fidelity);
    setGetAmount(getAmount);
  }

  useEffect(() => {
    //Загрузка валют и направлений
    const init = async () => {
      try {
        setLoading(true);

        // отправим id реферала, получим cookies
        const urlParams = new URLSearchParams(window.location.search);
        const rid = urlParams.get('rid');
        if(rid) sendRefId({rid: rid});        
        
        setGiveFields([]);
        setGetFields([]);
        setGiveFieldValues(null);
        setGetFieldValues(null);
        // если был переход из каталога обменников, то загрузим код направления
        let freshDirId = 0;
        if(!directionId && (giveCode || getCode)) {
          let answer = (await getDirectionId({ from: giveCode, to: getCode })).data;
          setDirectionId(answer);
          freshDirId = answer;
        }       
        const dir = (await fetchUserDirection({ id: directionId || freshDirId })).data;        
        setGiveFields(dir.giveFields);
        setGetFields(dir.getFields);

        const [loadDataResult, newStatus] = await Promise.all([
          loadData(fetchCurrencies, fetchDirections, dir.fromId, dir.toId),
          fetchOrderStatus({ id: dir.statusId }).then(result => result.data)
        ]);
        const { giveList, giveSelected: newGiveSelected, getList, getSelected: newGetSelected, directionId: newDirectionId,
          price: newPrice } = loadDataResult;
        setEmail(userInfo?.mail);

        setCurrenciesGive(giveList);
        setGiveSelected(newGiveSelected);
        setGetSelected(newGetSelected);
        setCurrenciesGet(getList);
        // setDirectionId(newDirectionId);
        setPrice(newPrice);

        let s = newStatus?.list.reduce((acc, it) => {
          acc[it.statusType] = it.text;
          return acc;
        }, {});
        setStatus(s);

        if (directionId != parseInt(searchParams.get("directionId"))) {
          setGiveAmount("");
          setGetAmount("");
          setWallet("");          
          setWalletChecked(null);
        }
        else {
          const giveAmount = (getAmount / newPrice).toFixed(newGiveSelected.fidelity);
          setGiveAmount(giveAmount);          
          await updatePriceAndReserve(newPrice, newGiveSelected, newGetSelected, newDirectionId);          
        }        
      }
      finally {
        setLoading(false);
      }
    };

    init();
  }, [directionId]);

  useEffect(() => {
    checkValues(limit, reserve, giveAmount, getAmount, giveSelected, getSelected, setLimitsGiveWarn, setLimitsGetWarn);
  }, [giveAmount, getAmount]);

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
          <div className={styles.dataBar}>
            <h3>Создание заявки</h3>
            {status?.deadlines}
          </div>

          <div className={styles.dataBar}>
            <h3>Отдаете</h3>
            <div className={styles.twoCol}>
              <div>
                <ComboList rows={currenciesGive} selectedId={giveSelected?.id} setSelectedId={handleLoadDirectionsGet} />
                {formattedPrice && (
                  <p>
                    Курс: &nbsp;{formattedPrice}
                  </p>
                )}              
              </div>

              <div>
                <label className={styles.alignRight}>
                  Сумма*: &nbsp;
                  <input className={styles.currencyAmount} placeholder={limit?.minSumGive.toFixed(giveSelected?.fidelity)}
                    value={giveAmount} onChange={e => updateGiveAmount(e.target.value)} />
                </label>
                {limitsGiveWarn && <div className={styles.warning}>{limitsGiveWarn}</div>}
                {limit?.minSumGive > 0 && (
                  <p>
                    Мин: &nbsp;
                    {limit.minSumGive.toFixed(giveSelected.fidelity)} {giveSelected?.code}
                  </p>
                )}
                {limit?.maxSumGive > 0 && (
                  <p>
                    Макс: &nbsp;
                    {limit.maxSumGive.toFixed(giveSelected.fidelity)} {giveSelected?.code}
                  </p>
                )}
                {giveFields.map(f => (
                  <Field key={f.name} name={f.name} value={f.value} onChange={handleGiveFieldChange}
                    placeholder={f.hint} isRequired={f.isRequired} />
                ))}
              </div>
            </div>
          </div>

          <div className={styles.dataBar}>
            <h3>Получаете</h3>
            <div className={styles.twoCol}>
              <div>
                <ComboList rows={currenciesGet} selectedId={getSelected?.id} setSelectedId={handleLoadDirectionsGive} />
                <p>
                  На счет*:
                </p>
                <AddressChecker currencyId={getSelected?.id} wallet={wallet} setWallet={setWallet} className={styles.wallet} 
                  result={walletChecked} setResult={setWalletChecked} />
                
              </div>

              <div>
                <label className={styles.alignRight}>
                  Сумма*: &nbsp;
                  <input className={styles.currencyAmount} placeholder={limit?.minSumGet.toFixed(getSelected?.fidelity)}
                    value={getAmount} onChange={e => updateGetAmount(e.target.value)} />
                </label>
                {limitsGetWarn && <div className={styles.warning}>{limitsGetWarn}</div>}
                {limit?.minSumGet > 0 && (
                  <p>
                    Мин: &nbsp;
                    {limit.minSumGet.toFixed(getSelected.fidelity)} {getSelected?.code}
                  </p>
                )}
                {limit?.maxSumGet > 0 && (
                  <p>
                    Макс: &nbsp;
                    {limit.maxSumGet.toFixed(getSelected.fidelity)} {getSelected?.code}
                  </p>
                )}
                {getFields.map(f => (
                  <Field key={f.name} name={f.name} value={f.value} onChange={handleGetFieldChange}
                    placeholder={f.hint} isRequired={f.isRequired} />
                ))}
              </div>
            </div>
          </div>

          {status?.instructions && (
            <div className={styles.dataBar}>
              <h3>Инструкции по обмену</h3>
              {status?.instructions}              
            </div>
          )}

          <div className={styles.dataBar}>          
              <div>E-mail*: &nbsp; <input className={styles.field} value={email} onChange={e => setEmail(e.target.value)} /></div>
              <div style={{marginTop: "1em"}}><button className={styles.sendButton} onClick={handleSend}>Отправить</button></div>
          </div>
        </main>

        <Footer styles={styles} />
      </div>
    </div>
  );
}

//Вывод доп.полей
function Field({ name, value, onChange, placeholder, isRequired }) {
  return (
    <div style={{marginTop: "1em"}}>
      <label>{name}{isRequired ? <>*: &nbsp;</> : ":"}
        <input value={value} onChange={(e) => onChange(name, e.target.value)} className={styles.field} placeholder={placeholder} />
      </label>
    </div>
  );
}