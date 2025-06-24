import { useParams, useNavigate, Link } from "react-router-dom";
import { toast } from 'react-toastify';
import { useContext, useState, useEffect, useMemo } from "react";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { CommonAPIContext } from "@/context/CommonAPIContext.jsx";
import ComboList from "@/routes/components/ComboList";
import CheckboxList from "@/routes/components/CheckboxList";
import { ROUTES } from "@/links";
import AddressChecker from "@/routes/components/AddressChecker";
import styles from "../admin.module.css";

// Commission component (unchanged, but now gets dynamic props)
function Commission({ message, obj, currencyCode }) {
  return (obj?.fixedFee > 0 || obj?.percentageFee > 0) ? (
    <>
      {message}:&nbsp;
      <div className={styles.horzList}>
        {obj?.fixedFee > 0 && <div>{obj.fixedFee} {currencyCode}</div>}
        {obj?.percentageFee > 0 && <div>{obj.percentageFee} %</div>}
      </div>
    </>
  ) : null;
}

function Limits({ message, obj, currencyCode }) {
  return (obj?.minAmount > 0 || obj?.maxAmount > 0) ? (
    <>
      {message}:&nbsp;
      <div className={styles.horzList}>
        {obj?.minAmount > 0 && <div>Мин: {obj.minAmount} {currencyCode} </div>}
        {obj?.maxAmount > 0 && <div>Макс: {obj.maxAmount} {currencyCode}</div>}
      </div>
    </>
  ) : null;
}

export default function EditCurrency() {
  const { id } = useParams();
  const { currencies, setCurrencies, currencyFields, loading: currencyLoading } = useContext(CurrencyContext);
  const { fetchFieldsForCurrency, fetchReserve } = useContext(CommonAPIContext);
  const { sendCurrency, sendFieldsForCurrency, sendReserve, reloadReserve, fetchCurrencyValidators, fetchPayin, fetchPayout } = useContext(AdminAPIContext);
  const { activeRole, loading: rolesLoading } = useContext(UsersRolesContext);
  const codes = Array.from(new Set(currencies.map(it => it.code)));
  const navigate = useNavigate();

  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(!!id);
  const [reserveInput, setReserveInput] = useState();
  const [wallet, setWallet] = useState();
  const [validators, setValidators] = useState([]);
  const [payin, setPayin] = useState({});
  const [payout, setPayout] = useState({});
  const [fieldsGive, setFieldsGive] = useState([]);
  const [fieldsGet, setFieldsGet] = useState([]);
  const [showFields, setShowFields] = useState("give");
  const [currencyData, setCurrencyData] = useState({
    name: "",
    code: "",
    xmlCode: "",
    fidelity: 2,
    isEnabled: true,
    acctValidator: "AUTO",
    acctChain: "AUTO",
  });
  const [reserve, setReserve] = useState({reserveType: "off"});
  const [walletChecked, setWalletChecked] = useState(null);
  const processingMethods = [
    {name: "Вручную", id: "manual"},
    {name: "Bybit", id: "bybit"},
    {name: "Mexc", id: "mexc"}
  ]
  const exchangeNames = [
    "Bybit",
    "Mexc"
  ]

  useEffect(() => {
    const loadData = async () => {
      try {
        setLoading(true);
        const [fieldsAnswer, reserveAnswer, payinAnswer, payoutAnswer] = await Promise.all([
          fetchFieldsForCurrency({ id }).then(result => result.data),
          fetchReserve({ code: currencyData.code }).then(result => result.data),
          currencyData.payin == "manual" ? Promise.resolve(null) : fetchPayin({ exchange: currencyData.payin }).then(result => result.data),
          currencyData.payout == "manual" ? Promise.resolve(null) : fetchPayout({ exchange: currencyData.payout }).then(result => result.data),
        ]);
        setFieldsGive(fieldsAnswer.give);
        setFieldsGet(fieldsAnswer.get);
        setReserve(reserveAnswer);
        setReserveInput(reserveAnswer.value);
        setPayin(payinAnswer);        
        setPayout(payoutAnswer);
      } finally {
        setLoading(false);
      }
    };
    if (id && currencyData.code.trim().length > 0 && !saving) {
      loadData();
    }
  }, [id, currencyData.payin, currencyData.payout, saving]);

  useEffect(() => {
    const currency = currencies.find(it => it.id == id);
    if (currency) {
      setCurrencyData({
        ...currency,
        acctValidator: currency.acctValidator?.length === 0 ? "OFF" : currency.acctValidator,
      });
    }
  }, [currencyLoading]);

  useEffect(() => {
    const loadValidators = async () => {
      const answer = await fetchCurrencyValidators();
      setValidators(answer.data);
    };
    loadValidators();
  }, []);

  const handleChange = (e) => {
    const { name, type, checked, value } = e.target;
    setCurrencyData(prev => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));
  };

  const handleFileReserve = async ({ code }) => {
    try {
      setSaving(true);
      const answer = await reloadReserve({ baseCurrency: code });
      toast.info(answer.data);
    } finally {
      setSaving(false);
    }
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      const updatedData = {
        ...currencyData,
        acctValidator: currencyData.acctValidator === "OFF" ? "" : currencyData.acctValidator,
        ...(parseInt(id) ? { id: parseInt(id) } : {}),
        payinChain: currencyData.payin === "manual" ? "" : (currencyData.payinChain || payin[currencyData.payin]?.chains[0]?.code),
        payoutChain: currencyData.payout === "manual" ? "" : (currencyData.payoutChain || payout[currencyData.payout]?.chains[0]?.code),
      };
      const answer = await sendCurrency({ currency: updatedData });
      updatedData.id = answer.data.id;

      setCurrencies(prev => {
        const exists = prev.some(currency => currency.id === answer.data.id);
        return exists
          ? prev.map(currency => currency.id === answer.data.id ? { ...currency, ...updatedData } : currency)
          : [...prev, updatedData];
      });
      await sendFieldsForCurrency({ id: answer.data.id, giveFields: fieldsGive, getFields: fieldsGet });
      if (activeRole.isEditReserve) {
        await sendReserve({
          baseCurrency: currencyData.code,
          reserve: {
            ...reserve,
            value: reserveInput,
            reserveCurrency: reserve.reserveType === "fromExchange" ? "USDT" : reserve.reserveCurrency,
            exchangeName: reserve.reserveType === "fromExchange" ? reserve.exchangeName : "",
          },
        });
      }
      toast.info(answer.message);
      navigate(ROUTES.CURRENCIES);
    } finally {
      setSaving(false);
    }
  };

  // Dynamically compute chain objects with useMemo
  const payinChainObj = useMemo(() => {
    return payin && currencyData.payin && currencyData.payin !== "manual" && payin[currencyData.payinCode]?.chains
      ? payin[currencyData.payinCode].chains.find(chain => chain.code === currencyData.payinChain) || null
      : null;
  }, [payin, currencyData.payin, currencyData.payinChain]);

  const payoutChainObj = useMemo(() => {
    return payout && currencyData.payout && currencyData.payout !== "manual" && payout[currencyData.payoutCode]?.chains
      ? payout[currencyData.payoutCode].chains.find(chain => chain.code === currencyData.payoutChain) || null
      : null;
  }, [payout, currencyData.payout, currencyData.payoutChain]);

  if (saving) return <p>Сохранение...</p>;
  if (loading || rolesLoading || currencyLoading || !validators) return <p>Загрузка...</p>;

  const validationMap = { CARD: "Карта", AUTO: "Авто" };
  const chainMap = { AUTO: "Авто", CARDLUNA: "Visa, MasterCard, Мир" };
  const firstWord = activeRole.isEditCurrency ? "Редактировать" : "Просмотреть";
  const reserveNames = [
    { id: "off", name: "Отключить" },
    { id: "fromFile", name: "Из файла" },
    { id: "fromExchange", name: "С биржи" },
    { id: "manual", name: "Задать вручную" },
  ];

  return (
    <div id="edit-currency">
      {id ? <h2>{firstWord} валюту - {currencyData.name}</h2> : <h2>Создать валюту</h2>}
      <div className={styles.tableTwoColLayout} style={{ marginBottom: "1em" }}>
        Название: <input name="name" value={currencyData.name} onChange={handleChange} />
        Код: <input name="code" value={currencyData.code} onChange={handleChange} style={{ width: "50%" }} />
        XML-код: <input name="xmlCode" value={currencyData.xmlCode} onChange={handleChange} style={{ width: "50%" }} />
        Точность:
        <div className={styles.horzList}>
          <input name="fidelity" value={currencyData.fidelity} onChange={handleChange} type="number" style={{ width: "10em" }} /> знаков после запятой
        </div>
        {activeRole.isEditCurrency ? (
          <>
            Прием:
            <div className={styles.horzList}>
              <ComboList rows={processingMethods} name="payin" selectedId={currencyData?.payin} setSelectedId={value => setCurrencyData({ ...currencyData, payin: value })} />
              
              {currencyData.payin !== "manual" && payin && (
              <ComboList
                rows={Object.entries(payin).map(([key, value]) => ({ id: key, name: value?.name || key }))}
                name="payinCode"
                selectedId={currencyData?.payinCode}
                setSelectedId={value => setCurrencyData({
                  ...currencyData,
                  payinCode: value,
                  payinChain: payin[value]?.chains?.at(0)?.code || "",
                })}
              />
              )}
              
              {currencyData.payin !== "manual" && payin && currencyData?.payinCode && payin[currencyData.payinCode]?.chains && (
                <>
                  Сеть:
                  <ComboList
                    rows={payin[currencyData.payinCode].chains.map(it => ({ id: it.code, name: it.name || it.code }))}
                    name="payinChain"
                    selectedId={currencyData?.payinChain}
                    setSelectedId={value => setCurrencyData({ ...currencyData, payinChain: value })}
                  />
                </>
              )}
            </div>
          </>
        ) : (
          <>
            Прием:
            <div className={styles.horzList}>
              {currencyData?.payin == "manual" ? "Вручную" : (
                <>
                  currencyData?.payin
                  <div>{payin[currencyData?.payinCode]?.name || payin[currencyData?.payinCode]?.code || "Ошибка"}</div>
                </>
              )}
              {currencyData?.payinChain && (
                <>
                  Сеть:
                  {payin[currencyData?.payinCode]?.chains?.find(chain => chain.code === currencyData.payinChain)?.name || currencyData.payinChain}
                </>
              )}
            </div>
          </>
        )}
        <Commission message="Комиссия на прием" obj={payinChainObj} currencyCode={currencyData.payinCode} />
        <Limits message="Лимиты на прием" obj={payinChainObj} currencyCode={currencyData.payinCode} />
        {payinChainObj && (<>Количество подтверждений: <span>{payinChainObj.confirmation}</span></>)}

        {activeRole.isEditCurrency ? (
          <>
            Отправка:
            <div className={styles.horzList}>
              <ComboList rows={processingMethods} name="payout" selectedId={currencyData?.payout} setSelectedId={value => setCurrencyData({ ...currencyData, payout: value })} />
              
              {currencyData.payout !== "manual" && payout && (
              <ComboList
                rows={Object.entries(payout).map(([key, value]) => ({ id: key, name: value?.name || key }))}
                name="payoutCode"
                selectedId={currencyData?.payoutCode}
                setSelectedId={value => setCurrencyData({
                  ...currencyData,
                  payoutCode: value,
                  payoutChain: payout[value]?.chains?.at(0)?.code || "",
                })}
              />
              )}
              
              {currencyData.payout !== "manual" && payout && currencyData?.payoutCode && payout[currencyData.payoutCode]?.chains && (
                <>
                  Сеть:
                  <ComboList
                    rows={payout[currencyData.payoutCode].chains.map(it => ({ id: it.code, name: it.name || it.code }))}
                    name="payoutChain"
                    selectedId={currencyData?.payoutChain}
                    setSelectedId={value => setCurrencyData({ ...currencyData, payoutChain: value })}
                  />
                </>
              )}
            </div>
          </>
        ) : (
          <>
            Прием:
            <div className={styles.horzList}>
              {currencyData?.payout == "manual" ? "Вручную" : (
                <>
                  currencyData?.payout
                  <div>{payout[currencyData?.payoutCode]?.name || payout[currencyData?.payoutCode]?.code || "Ошибка"}</div>
                </>
              )}
              {currencyData?.payoutChain && (
                <>
                  Сеть:
                  {payout[currencyData?.payoutCode]?.chains?.find(chain => chain.code === currencyData.payoutChain)?.name || currencyData.payoutChain}
                </>
              )}
            </div>
          </>
        )}
        <Commission message="Комиссия на отправку" obj={payoutChainObj} currencyCode={currencyData.payoutCode} />
        <Limits message="Лимиты на отправку" obj={payoutChainObj} currencyCode={currencyData.payoutCode} />

        Резерв:
        {activeRole.isEditReserve ? (
          <div className={styles.horzList}>
            <ComboList
              rows={reserveNames}
              selectedId={reserve?.reserveType}
              setSelectedId={value => setReserve({ ...reserve, reserveType: value })}
            />
            {reserve?.reserveType === "manual" && (
              <>
                <input type="number" style={{ width: "10em" }} value={reserveInput} onChange={e => setReserveInput(parseFloat(e.target.value))} />
                <ComboList rows={codes} selectedId={reserve?.reserveCurrency} setSelectedId={e => setReserve({ ...reserve, reserveCurrency: e })} />
              </>
            )}
            {reserve?.reserveType === "fromExchange" && (
              <ComboList rows={exchangeNames} selectedId={reserve?.exchangeName} setSelectedId={e => setReserve({ ...reserve, exchangeName: e })} />
            )}
            {(reserve?.reserveType === "fromFile" || reserve?.reserveType === "fromExchange") && (
              <>{reserve.value} {reserve.reserveCurrency}</>
            )}
            {reserve?.reserveType === "fromFile" && (
              <Link className={styles.links} onClick={() => handleFileReserve({ code: currencyData.code })}>Обновить</Link>
            )}
            {reserve == null && "Ошибка загрузки резерва!"}
          </div>
        ) : (
          <div className={styles.horzList}>
            {reserveNames.find(it => it.id === reserve?.reserveType)?.name || "Отключен"}
            {reserve?.reserveType !== "off" && <>, {reserve.value} {reserve.reserveCurrency}</>}
          </div>
        )}
        <label className={[styles.spanTwoColumns, styles.oneLine].join(" ")}>
          <input type="checkbox" name="isEnabled" checked={currencyData.isEnabled} onChange={handleChange} />
          Активная валюта
        </label>
        Валидатор счета:
        <div className={styles.horzList}>
          <ComboList
            rows={Object.keys(validators)
              .map(it => validationMap[it] ? { id: it, name: validationMap[it] } : it)
              .concat({ id: "OFF", name: "Отключить" })}
            selectedId={currencyData.acctValidator}
            setSelectedId={value => setCurrencyData({ ...currencyData, acctValidator: value })}
          />
          {validators[currencyData.acctValidator] && (
            <>
              Сеть:
              <ComboList
                rows={[{ id: "", name: "Выберите.." }, ...validators[currencyData.acctValidator].map(it => chainMap[it] ? { id: it, name: chainMap[it] } : it)]}
                selectedId={currencyData.acctChain}
                setSelectedId={value => setCurrencyData({ ...currencyData, acctChain: value })}
              />
            </>
          )}
        </div>
        Поля:
        <div className={styles.horzList}>
          <ComboList
            rows={[{ id: "give", name: "Отдаю" }, { id: "get", name: "Получаю" }]}
            selectedId={showFields}
            setSelectedId={setShowFields}
          />
          <button onClick={() => navigate(ROUTES.EDIT_CURRENCY_FIELD)}>Добавить поле..</button>
        </div>
      </div>

      {showFields === "give" && (
        <CheckboxList rows={currencyFields} checkedKeys={fieldsGive} onCheckChange={setFieldsGive} className={styles.checkboxList} />
      )}
      {showFields === "get" && (
        <CheckboxList rows={currencyFields} checkedKeys={fieldsGet} onCheckChange={setFieldsGet} className={styles.checkboxList} />
      )}

      <h3>Проверить адрес:</h3>
      <AddressChecker
        currencyId={id}
        chain={currencyData?.acctChain}
        wallet={wallet}
        setWallet={setWallet}
        result={walletChecked}
        setResult={setWalletChecked}
      />

      <div className={styles.actionButtons} style={{ marginTop: "1em" }}>
        {activeRole.isEditCurrency && <button className={styles.save} onClick={handleSave}>Сохранить</button>}
      </div>
    </div>
  );
}