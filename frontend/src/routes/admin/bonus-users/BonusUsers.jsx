import ComboList from "@/routes/components/ComboList";
import { useContext, useEffect, useState } from "react";
import styles from "../admin.module.css";
import { cashbackTypes } from "@/routes/Index";
import { AdminAPIContext } from "@/context/AdminAPIContext";
import Pagination from "@/routes/components/Pagination";
import Nested from "@/routes/components/Nested/Nested";
import TableUserPayouts from "./TableUserPayouts";
import { toast } from "react-toastify";

export default function BonusUsers() {
    const [options, setOptions] = useState();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [cashbackStatus, setCashbackStatus] = useState();
    const [dateStart, setDateStart] = useState();
    const [dateEnd, setDateEnd] = useState();
    const [email, setEmail] = useState();
    const [isVisible, setIsVisible] = useState(false);
    const [currentPage, setCurrentPage] = useState(0);
    const rowsPerPage = 10;
    const { fetchCashbackStatus, fetchOptions, sendOptions, sendCashbackConfirm } = useContext(AdminAPIContext);    

    async function load() {
        try {
            setLoading(true);
            const start = currentPage * rowsPerPage;
            let [users, opts] = await Promise.all([
                fetchCashbackStatus({start: start, count: rowsPerPage, userMail: email,
                dateStart: dateStart?.concat("T00:00"), dateEnd: dateEnd?.concat("T00:00")}).then(res => res.data),
                fetchOptions().then(res => res.data)
            ]);
            setCashbackStatus(users);            
            setOptions(opts);            
        } finally {
            setLoading(false);
        }
    }

    async function handleConfirm({userId}) {
        try {
            setSaving(true);
            let answer = await sendCashbackConfirm({userId: userId});
            toast.info(answer.message);
            load();
        } finally {
            setSaving(false);
        }
    }

    async function save() {
        try {
            setSaving(true);
            let answer = await sendOptions({ opt: options });
            toast.info(answer.message);
        } finally {
            setSaving(false);
        }
    }

    function handleReset() {
        currentPage == 0 ? load() : setCurrentPage(0);
    }

    useEffect(() => {
        load();
    }, [currentPage])

    if (loading) {
        return <p>Загрузка...</p>;
    }
    if (saving) {
        return <p>Сохранение...</p>;
    }

    return(
        <>
            <h2>Бонусы для пользователей</h2>
            <div className={styles.horzList}>                
                <input type="number" value={options.cashbackPercent} onChange={e => setOptions({...options, cashbackPercent: e.target.value})}/> %&nbsp;
                <ComboList rows={cashbackTypes} selectedId={options.cashbackType}
                    setSelectedId={e => setOptions({...options, cashbackType: e})} />
            </div>       
            Можно задать для каждого пользователя свой процент.
            <p><b>Всего начислено:</b>
                &nbsp;&nbsp;
                {cashbackStatus?.sum || 0.0} USDT
            </p>
            <p><b>Всего выплачено:</b>
                &nbsp;&nbsp;
                {cashbackStatus?.payed || 0.0} USDT
            </p>
            <p><b>Баланс:</b>
                &nbsp;&nbsp;
                {cashbackStatus?.sum - cashbackStatus?.payed || 0.0} USDT
            </p>
            <button className={styles.save} onClick={save}>Сохранить</button>

            <h3 style={{marginTop: "2em"}}>Запросы на выплату</h3>
            <Nested title="Фильтр" isVisible={isVisible} setIsVisible={setIsVisible} styles={styles}
                child={
                <>
                    <div style={{marginBottom: "1em"}}>
                        <label>
                            Дата от: <input type="date" value={dateStart} name="dateStart" onChange={e => setDateStart(e.target.value)}/>
                        </label>
                        <label>
                            Дата до: <input type="date" value={dateEnd} name="dateEnd" onChange={e => setDateEnd(e.target.value)} />
                        </label>                        
                    </div>
                    <div style={{marginBottom: "1em"}}>
                        <label>
                            Почта пользователя: <input type="text" value={email} name="email" onChange={e => setEmail(e.target.value)} />
                        </label>
                    </div>
                    
                    <button onClick={handleReset}>Применить</button>    
                </>
                }
            />

            <Pagination
                total={cashbackStatus?.payouts?.first}
                currentPage={currentPage}
                rowsPerPage={rowsPerPage}
                onPageChange={setCurrentPage}
                styles={styles}
            />
            
            <TableUserPayouts data={cashbackStatus?.payouts?.second} handleConfirm={handleConfirm} />
            
            <Pagination
                total={cashbackStatus?.payouts?.first}
                currentPage={currentPage}
                rowsPerPage={rowsPerPage}
                onPageChange={setCurrentPage}
                styles={styles}
            />

        </>
    )
}