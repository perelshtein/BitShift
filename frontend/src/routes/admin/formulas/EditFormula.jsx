import { useParams } from "react-router-dom";
import { useContext, useEffect, useState } from "react";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { toast } from "react-toastify";
import ComboList from "@/routes/components/ComboList.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { CommonAPIContext } from "@/context/CommonAPIContext.jsx";
import styles from "../admin.module.css";

export default function EditFormula() {
    const { id } = useParams();
    const { activeRole, loading: roleLoading } = useContext(UsersRolesContext);
    const [from, to] = (id?.split("-") || [undefined, undefined]);
    const { currencies, loading: currenciesLoading } = useContext(CurrencyContext);
    const codes = Array.from(new Set(currencies.map(it => it.code)));
    const { fetchFormulaVariants, sendFormula } = useContext(AdminAPIContext);
    const { fetchFormula } = useContext(CommonAPIContext);
    const searchCaseList = [
        { id: "auto", name: "Авто" },
        { id: "CoinMarketCap", name: "CoinMarketCap" },
        { id: "fromFile", name: "Из файла" }
    ];
    const errorCaseList = [
        { id: "auto", name: "Найти новый курс (Авто)" },
        { id: "CoinMarketCap", name: "Переключить на CoinMarketCap" },
        { id: "off", name: "Отключить" }
    ];
    const [source, setSource] = useState("auto");
    const [errorCase, setErrorCase] = useState("auto");
    const [currencyGive, setCurrencyGive] = useState();
    const [currencyGet, setCurrencyGet] = useState();
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [formulaLoading, setFormulaLoading] = useState(false);
    const [cachedFormula, setCachedFormula] = useState();
    const [variants, setVariants] = useState({
        auto: null,
        fromFile: null,
        coinMarketCap: null,
    });
    const [selectedVariant, setSelectedVariant] = useState();

    const checkFormula = async () => {
        setFormulaLoading(true);
        try {
            setVariants((prev) => ({ ...prev, [source]: null }));
            const answer = (await fetchFormulaVariants({ from: currencyGive, to: currencyGet, searchCase: source })).data;
            setVariants((prev) => ({ ...prev, [source]: answer }));
            if (source === "auto") {
                console.log("Setting selected variant...");
                // используем непосредственно answer, а не state, потому что state - большой тормоз
                setSelectedVariant(answer.list[0]?.path);
            }
        }
        finally {
            setFormulaLoading(false);
        }
    }

    useEffect(() => {
        const loadCachedFormula = async () => {
            setLoading(true);
            try {
                const answer = (await fetchFormula({ from: from, to: to })).data;
                setCachedFormula(answer);
                setCurrencyGive(from);
                setCurrencyGet(to);
                setErrorCase(answer.reserve);
            }
            finally {
                setLoading(false);
            }
        }
        if (id && !saving) loadCachedFormula();
        else {
            setCurrencyGive(codes[0]); // контекст загружается только в UseEffect
            setCurrencyGet(codes[1]);
        }
    }, [saving]);

    const EditFormulaSection = () => (
        <>
            {id && (<h3>Новая формула</h3>)}
            <div className={styles.horzList}>
                Курс:
                <ComboList rows={searchCaseList} selectedId={source} setSelectedId={setSource} />

                <button onClick={checkFormula}>Найти курс</button>
            </div>

            {source === "auto" && variants?.[source] && (
                <div>
                    <ComboList
                        rows={variants[source].list.map(it => it.path)}
                        selectedId={selectedVariant}
                        setSelectedId={setSelectedVariant}
                    />
                    {selectedVariant && (
                        <div>
                            {(() => {
                                const selectedItem = variants[source].list.find(it => it.path === selectedVariant);
                                if (selectedItem) {
                                    return (
                                        <>
                                            <p><b>Цена:</b> {selectedItem.price} </p>
                                            <p><b>Формула:</b> {selectedItem.tag} </p>
                                            <p><b>Спред:</b> {roundDouble(selectedItem.spread)} %</p>
                                            <p><b>Разница с реф.ценой:</b> {calcRefPriceDiff(selectedItem.price)} %</p>
                                        </>
                                    );
                                }
                            })()}
                        </div>
                    )}
                    {variants[source].referentPrice && (
                        <p>
                            <b>Реф. цена:</b> {variants[source].referentPrice} ({variants[source].referentSource})
                        </p>
                    )}
                </div>
            )}

            {source === "CoinMarketCap" && variants?.[source] && (
                <div>
                    <p><b>Цена:</b> {variants[source].list[0]?.price} </p>
                    <p><b>Формула:</b> {variants[source].list[0]?.tag} </p>
                </div>
            )
            }

            {source === "fromFile" && variants?.[source] && (
                <div>
                    <p><b>Цена:</b> {variants[source].list[0]?.price} </p>
                    <p><b>Формула:</b> {variants[source].list[0]?.tag} </p>
                </div>
            )
            }

            {source === "auto" && (
                <label>
                    При ошибке:
                    <ComboList rows={errorCaseList} selectedId={errorCase} setSelectedId={setErrorCase} />
                </label>
            )
            }

            {source !== "auto" && (
                <p>
                    При ошибке: Отключить
                </p>
            )
            }

            <div className={styles.actionButtons}>
                <button onClick={handleSave} className={styles.save}>Сохранить</button>
            </div>
        </>
    )

    const handleSave = async () => {
        console.log(variants);
        let noNewFormula = Object.values(variants).every(value => value === null);
        if (noNewFormula && !cachedFormula) {
            toast.warn("Формула не задана. Нажмите Найти для поиска");
            return;
        }

        setSaving(true);
        try {
            let sparseVariant = source !== "auto" ? "off" : errorCase;
            let opt = {};
            let obj;
            if (!noNewFormula) {
                console.log("constructing new formula...")                
                if (source === "auto") opt = variants[source].list.find(it => it.path === selectedVariant);
                else opt = variants[source].list[0];
                obj = { from: currencyGive, to: currencyGet, tag: opt.tag, reserve: sparseVariant, isEnabled: true };
            }
            else obj = ({...cachedFormula, reserve: sparseVariant});

            let answer = await sendFormula({ formula: obj });
            toast.info(answer.message);
        }
        finally {
            setSaving(false);
        }
    }

    const roundDouble = (spread) => {
        if (Math.abs(spread) < 0.01) {
            spread = spread.toFixed(6);
        } else {
            spread = spread.toFixed(2);
        }
        return spread;
    }

    const calcRefPriceDiff = (price) => {
        let max = Math.max(variants[source].referentPrice, price);
        let min = Math.min(variants[source].referentPrice, price);
        let spread = (max - min) / min * 100 // в процентах        
        return roundDouble(spread);
    }

    if (loading || currenciesLoading || formulaLoading || roleLoading) {
        return <p>Загрузка...</p>
    }

    if (saving) {
        return <p>Сохранение...</p>
    }

    const firstWord = activeRole.isEditCurrency ? "Редактировать" : "Просмотреть";
    return (
        <>

            {id ? <h2>{firstWord} формулу</h2> : <h2>Добавить формулу</h2>}
            {id && cachedFormula && (
                <>
                    <h3>Старая формула</h3>
                    <p>{cachedFormula.tag}</p>
                    <p><b>Отдаю:</b> {cachedFormula.from}</p>
                    <p><b>Получаю:</b> {cachedFormula.to}</p>
                    <p><b>Курс:</b> {cachedFormula.price}</p>
                    <p><b>Обратный курс:</b> {cachedFormula.price ? 1 / cachedFormula.price : 0}</p>
                    <p><b>При ошибке:</b> {errorCaseList.find(it => it.id == cachedFormula.reserve).name}</p>
                    <label>
                        <input style={{ marginLeft: "0" }} type="checkbox" checked={cachedFormula.isEnabled}
                            onChange={e => setCachedFormula(prev => ({ ...prev, isEnabled: e.target.checked }))} />
                        Включена
                    </label>
                </>
            )}
            {!id && (<div>
                <label>
                    Отдаю:
                    <ComboList rows={codes} selectedId={currencyGive} setSelectedId={setCurrencyGive} />
                </label>
                <label>
                    Получаю:
                    <ComboList rows={codes} selectedId={currencyGet} setSelectedId={setCurrencyGet} />
                </label>
            </div>)}

            {activeRole.isEditCurrency && <EditFormulaSection />}
        </>
    );
}