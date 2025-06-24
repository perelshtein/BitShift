import { useParams, useNavigate } from "react-router-dom";
import { useContext, useState, useEffect } from "react";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { CommonAPIContext } from "@/context/CommonAPIContext.jsx";
import { ROUTES } from "@/links";
import { toast } from "react-toastify";
import styles from "../admin.module.css";

export default function EditDirection() {
    const { id } = useParams();
    const { currencies, loading: currenciesLoading } = useContext(CurrencyContext);
    const { activeRole, loading: rolesLoading } = useContext(UsersRolesContext);
    const { fetchFormula, fetchDirection } = useContext(CommonAPIContext);
    const { fetchOrderStatuses, sendDirection } = useContext(AdminAPIContext);
    const [currencyGive, setCurrencyGive] = useState();
    const [currencyGet, setCurrencyGet] = useState();
    const [direction, setDirection] = useState({ isActive: true, isExport: true, profit: 0.0, minSum: 0.0, maxSum: 0.0 });
    const [orderStatus, setOrderStatus] = useState();
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);

    const navigate = useNavigate();
    const [price, setPrice] = useState(0);

    useEffect(() => {
        const loadData = async () => {
            try {
                setLoading(true);
                let [orderStatuses, dir] = await Promise.all([
                    fetchOrderStatuses().then(result => result.data),
                    id ? fetchDirection({ id: id }).then(result => result.data) : Promise.resolve(null)
                ]);

                setOrderStatus(orderStatuses);
                setCurrencyGive(dir?.fromId || currencies.at(0)?.id);
                setCurrencyGet(dir?.toId || currencies.at(-1)?.id);
                if (!dir) {
                    dir = {};
                    dir.minSumCurrencyId = currencies.at(0)?.id;
                    dir.maxSumCurrencyId = currencies.at(0)?.id;
                    dir.statusId = orderStatuses.at(0)?.id;
                }
                setDirection(prev => ({ ...prev, ...dir }));
            } finally {
                setLoading(false);
            }
        };
        if (currencies) loadData();
    }, [currencies]);

    // const currencyGiveName = currencies.filter(it => it.id == currencyGive).map(it => it.name);    
    // const currencyGetName = currencies.filter(it => it.id == currencyGet).map(it => it.name);
    const currencyGiveCode = currencies.filter(it => it.id == currencyGive).map(it => it.code);
    const currencyGetCode = currencies.filter(it => it.id == currencyGet).map(it => it.code);

    useEffect(() => {
        const updatePrice = async () => {
            if (currencyGive == currencyGet) {
                setPrice(undefined);
            }
            else {
                // console.log(currencyGiveCode, currencyGetCode);
                let result = await fetchFormula({ from: currencyGiveCode, to: currencyGetCode });
                setPrice(result.data);
            }
        }
        updatePrice();
    }, [currencyGive, currencyGet])

    const handleSave = async () => {
        try {
            setSaving(true);
            let updated = {
                ...direction,
                id: parseInt(id) || 0,
                fromId: currencyGive,
                toId: currencyGet,
                formulaId: price?.id || 0
            }
            const answer = await sendDirection({ direction: updated });
            toast.info(answer.message);
            answer.data.warnings?.forEach(it =>
                toast.warn(it)
            )
        }
        finally {
            setSaving(false);
        }
    }

    // хитрый handleChange с поддержкой ComboList!
    const handleChange = (e) => {
        // Check if e is a synthetic event or custom object
        const name = e?.target?.name || e.name;
        const value = e?.target?.type === "checkbox" ? e.target.checked : e?.target?.value || e.value;

        setDirection((prev) => ({
            ...prev,
            [name]: value,
        }));
    };

    function Caption({ id }) {
        if (!activeRole.isEditDirection) {
            return <h2>Просмотр направления</h2>;
        }
        return id ? <h2>Редактировать направление - {id}</h2> : <h2>Создать направление</h2>;
    }


    function ComboList({ rows, selectedId, setSelectedId, name }) {
        const handleChange = (e) => {
            if (setSelectedId) {
                if (name) setSelectedId({ name, value: e.target.value });
                else setSelectedId(e.target.value);
            }
        };

        return (
            <select onChange={handleChange} value={selectedId ?? undefined}>
                {rows.map((row) => (
                    <option
                        key={row.id ?? row}
                        value={row.id ?? row}
                    >
                        {row.name ?? row}
                    </option>
                ))}
            </select>
        );
    }


    if (currenciesLoading || rolesLoading || loading) {
        return <p>Загрузка...</p>
    }

    if (saving) {
        return <p>Сохранение...</p>
    }

    return (
        <>
            <Caption id={id} />
            <div>
                <label>
                    Отдаю:
                    <ComboList rows={currencies.map(it => ({ ...it, name: `${it.name} (${it.code})` }))} selectedId={currencyGive} setSelectedId={setCurrencyGive} />
                </label>
                <label>
                    Получаю:
                    <ComboList rows={currencies.map(it => ({ ...it, name: `${it.name} (${it.code})` }))} selectedId={currencyGet} setSelectedId={setCurrencyGet} />
                </label>
            </div>

            <label className={styles.oneLine}>
                <input type="checkbox" name="isActive" checked={direction.isActive} onChange={handleChange} />
                Активное направление
            </label>

            <label className={styles.oneLine}>
                <input type="checkbox" name="isExport" checked={direction.isExport} onChange={handleChange} />
                Экспорт в файл курсов
            </label>
            {/* 
        <label className={styles.oneLine}>
            <input type="checkbox" />
            Вкл проверку AML
        </label> */}

            <h3>Сумма</h3>
            <div>
                <label>
                    Минимум:
                    <input type="number" name="minSum" value={direction.minSum} onChange={handleChange} />
                    <ComboList name="minSumCurrencyId" rows={currencies} selectedId={direction.minSumCurrencyId} setSelectedId={handleChange} />
                </label>
            </div>
            <div>
                <label>
                    Максимум:
                    <input type="number" name="maxSum" value={direction.maxSum} onChange={handleChange} />
                    <ComboList name="maxSumCurrencyId" rows={currencies} selectedId={direction.maxSumCurrencyId} setSelectedId={handleChange} />
                </label>
            </div>

            <h3>Курс</h3>
            <p><b>Курс:</b> 1 {currencyGiveCode} = {price?.price || 0} {currencyGetCode}</p>
            <p><b>Обратный курс:</b> 1 {currencyGetCode} = {1 / price?.price || 0} {currencyGiveCode}</p>
            {price?.tag && (
                <p><b>Формула: </b>
                    {price.tag}</p>
            )}
            {price?.lastUpdated &&
                (<p><b>Обновлен: </b>
                    {new Date(price.lastUpdated).toLocaleString()}</p>)
            }
            {activeRole.isEditDirection && <button onClick={() => navigate(ROUTES.FORMULAS)}>Задать курс..</button>}

            <h3>Прибыль</h3>
            <input type="number" style={{ marginTop: 0 }} name="profit" value={direction.profit} onChange={handleChange} /> %

            <h3>Статус заявок</h3>
            <div className={styles.horzList}>
                {orderStatus ? (<ComboList rows={orderStatus} name="statusId" selectedId={direction.statusId} setSelectedId={handleChange} />) : "Нет шаблонов"}
                {activeRole.isEditDirection &&
                    (<button onClick={() => navigate(orderStatus ? ROUTES.ORDER_STATUS : ROUTES.EDIT_ORDER_STATUS)}>
                        {orderStatus ? "Редактировать.." : "Добавить.."}
                    </button>)}
            </div>

            {activeRole.isEditDirection && (<div className={styles.actionButtons}>
                <button className={styles.save} onClick={handleSave}>Сохранить</button>
                <button className={styles.cancel}>Отмена</button>
            </div>)}
        </>
    )
}