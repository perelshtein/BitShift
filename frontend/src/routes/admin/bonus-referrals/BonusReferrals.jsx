import ComboList from "@/routes/components/ComboList";
import TableReferralPayouts from "./TableReferralPayouts";
import styles from "../admin.module.css";
import { cashbackTypes } from "@/routes/Index";
import { AdminAPIContext } from "@/context/AdminAPIContext";
import { useContext, useEffect, useState } from "react";
import Pagination from "@/routes/components/Pagination";
import Nested from "@/routes/components/Nested/Nested";
import { toast } from "react-toastify";

export default function BonusReferrals() {
    const { fetchReferralsStatus, fetchOptions, sendOptions, sendReferralsConfirm } = useContext(AdminAPIContext);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [referralsStatus, setReferralsStatus] = useState();
    const [dateStart, setDateStart] = useState();
    const [dateEnd, setDateEnd] = useState();
    const [email, setEmail] = useState();
    const [isVisible, setIsVisible] = useState(false);
    const [currentPage, setCurrentPage] = useState(0);
    const rowsPerPage = 10;
    const [options, setOptions] = useState();

    async function load() {
        try {
            setLoading(true);
            const start = currentPage * rowsPerPage;
            let [refs, opts] = await Promise.all([
                fetchReferralsStatus({start: start, count: rowsPerPage, refMail: email,
                dateStart: dateStart?.concat("T00:00"), dateEnd: dateEnd?.concat("T00:00")}).then(res => res.data),
                fetchOptions().then(res => res.data)
            ]);            
            setReferralsStatus(refs);
            setOptions(opts);
        } finally {
            setLoading(false);
        }
    }

    async function save() {        
        try {
            setSaving(true);
            let answer = await sendOptions({ opt: options });
            toast.info(answer.message);
        }
        finally {
            setSaving(false);            
        }
    }

    async function handleConfirm({rid}) {
        try {
            setSaving(true);
            let answer = await sendReferralsConfirm({rid: rid});
            toast.info(answer.data.message);
            load();
        } finally {
            setSaving(false);
        }
    }

    useEffect(() => {
        load();
    },[currentPage]);

    function handleReset() {
        currentPage == 0 ? load() : setCurrentPage(0);
    }

    if(loading) {
        return <p>Загрузка...</p>    
    }

    if(saving) {
        return <p>Сохранение...</p>    
    }

    return(
        <>
            <h2>Бонусы для рефералов</h2>
            Политика по умолчанию при обмене по реф.ссылке:
            <div className={styles.horzList}>                
                Начислить кэшбек <input type="number" value={options.referralPercent} onChange={e => setOptions({...options, referralPercent: e.target.value})}/> %&nbsp;
                <ComboList rows={cashbackTypes} selectedId={options.referralType}
                    setSelectedId={e => setOptions({...options, referralType: e})} />
            </div>             
            Можно задать для каждого реферала свой процент.
            <p><b>Всего начислено:</b>
                &nbsp;&nbsp;
                {referralsStatus?.sum || 0.0} USDT
            </p>
            <p><b>Всего выплачено:</b>
                &nbsp;&nbsp;
                {referralsStatus?.payed || 0.0} USDT
            </p>
            <p><b>Баланс:</b>
                &nbsp;&nbsp;
                {referralsStatus?.sum - referralsStatus?.payed || 0.0} USDT
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
                            Почта реферала: <input type="text" value={email} name="email" onChange={e => setEmail(e.target.value)} />
                        </label>
                    </div>
                    
                    <button onClick={handleReset}>Применить</button>    
                </>
                }
            />

            <Pagination
                total={referralsStatus?.payouts?.first}
                currentPage={currentPage}
                rowsPerPage={rowsPerPage}
                onPageChange={setCurrentPage}
                styles={styles}
            />
            
            <TableReferralPayouts data={referralsStatus?.payouts?.second} handleConfirm={handleConfirm} />
            
            <Pagination
                total={referralsStatus?.payouts?.first}
                currentPage={currentPage}
                rowsPerPage={rowsPerPage}
                onPageChange={setCurrentPage}
                styles={styles}
            />
        </>
    )
}