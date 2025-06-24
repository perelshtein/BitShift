import { useParams } from "react-router-dom";
import styles from "../admin.module.css";
import ComboList from "./ComboList";
import { orderStatusList as status } from '@/routes/Index';
import { useState, useContext, useEffect } from "react";
import { AdminAPIContext } from "@/context/AdminAPIContext";
import { toast } from "react-toastify";

export default function EditOrder() {
    const { id } = useParams();   
    const { sendOrders, fetchOrder } = useContext(AdminAPIContext);    
    // const [selectedStatus, setSelectedStatus] = useState(status[0].id);
    const [saving, setSaving] = useState(false);
    const [loading, setLoading] = useState(true);
    const [queryOptions, setQueryOptions] = useState({});

    const handleChange = (e) => {
        // Check if e is a synthetic event or custom object
        const name = e?.target?.name || e.name;
        const value = e?.target?.value || e.value;

        setQueryOptions((prev) => ({
            ...prev,
            [name]: value,
        }));
    };
    
    const handleSave = async() => {
        try {
            setSaving(true);
            let answer = await sendOrders({...queryOptions, ids: [id], rateGive: queryOptions.rateGive ? 1/queryOptions.rateGive : null});
            toast.info(answer.message);
        //   loadOrders();
        }
        finally {
            setSaving(false);
        }
    }

    useEffect(() => {
        const loadOrder = async() => {
            try {
                setLoading(true);
                const found = (await fetchOrder({id})).data;
                console.log(found);
                setQueryOptions({...found, rateGive: found.rateGive > 0 ? 1/found.rateGive : ""});
            }
            finally {
                setLoading(false);
            }
        }
        loadOrder();
    }, []);

    if(loading) {
        return (
            <p>Загрузка...</p>
        )
    }

    if(saving) {
        return (
            <p>Сохранение...</p>
        )
    }

    return (
        <>
            <h2>Редактировать заявку {id}</h2>
            <div className={styles.tableTwoColLayout}>
                <label htmlFor="user">Пользователь:</label>
                <div>{queryOptions?.userMail}</div>

                <label htmlFor="obmen">Обмен:</label>
                <div>{queryOptions?.from.amount} {queryOptions?.from.code} -&gt; {queryOptions?.to.amount} {queryOptions?.to.code}</div>

                <label htmlFor="statusList">Статус заявки:</label>
                <ComboList name="status" rows={status} selectedId={queryOptions?.status} setSelectedId={handleChange} />

                <label htmlFor="walletFrom">Счет Отдаю:</label>
                <input id="walletFrom" name="walletFrom" value={queryOptions?.walletFrom} onChange={handleChange} />

                {queryOptions?.isManualGive && (
                    <>
                    <label htmlFor="rateGive">Курс Отдаю:</label>                    
                    <div>
                    <input id="rateGive" name="rateGive" type="number" value={queryOptions?.rateGive} onChange={handleChange} /> 
                    &nbsp; USDT/{queryOptions?.from.code}
                    </div>
                    </>
                )}

                <label htmlFor="requisites">Реквизиты:</label>
                <textarea id="requisites" name="requisites" value={queryOptions?.requisites} onChange={handleChange} rows="10" cols="40" 
                    style={{ width: "100%" }} />
                {queryOptions?.isManualGet && (
                    <>
                    <label htmlFor="rateGet">Курс Получаю:</label>
                    <div>
                    <input id="rateGet" name="rateGet" type="number" value={queryOptions?.rateGet} onChange={handleChange} />
                    &nbsp; USDT/{queryOptions?.to.code}
                    </div>
                    </>
                )}
            </div>

            <div className={styles.actionButtons}>
                <button className={styles.save} onClick={handleSave}>Сохранить</button>
            </div>
        </>
    )
}