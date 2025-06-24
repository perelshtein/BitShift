import { useEffect, useState, useContext } from "react";
import styles from "@/routes/public/public.module.css";
import Header from "@/routes/public/modules/Header";
import Footer from "../../modules/Footer";
const SERVER_ROOT = import.meta.env.VITE_SERVER_ROOT;
import { AuthContext } from "@/context/AuthContext";
import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";
import { cashbackTypes } from "@/routes/Index";
import Warning from "@/routes/admin/Warning";
import Table from "./Table";
import Pagination from "@/routes/components/Pagination";
import Nested from "@/routes/components/Nested/Nested";
import { toast } from "react-toastify";
import { ROUTES } from "@/links";

export default function Referrals() {
    const [loading, setLoading] = useState(true);
    const [referralsStatus, setReferralsStatus] = useState();
    const [referrals, setReferrals] = useState();
    const [ totalCount, setTotalCount ] = useState();
    const [dateStart, setDateStart] = useState();
    const [dateEnd, setDateEnd] = useState();
    const [isVisible, setIsVisible] = useState(false);
    const [currentPage, setCurrentPage] = useState(0);
    const rowsPerPage = 10;
    const { loggedIn, userInfo } = useContext(AuthContext);
    const { fetchUserReferralsStatus, fetchUserReferrals, sendUserReferralsWithdraw } = useContext(WebsiteAPIContext);

    async function load() {
        try {
            setLoading(true);                
            const start = currentPage * rowsPerPage;
            let [status, refs] = await Promise.all([
                fetchUserReferralsStatus().then(res => res.data),
                fetchUserReferrals({start: start, count: rowsPerPage, dateStart: dateStart?.concat("T00:00"),
                    dateEnd: dateEnd?.concat("T00:00")}).then(res => res.data)
            ]);

            console.log("refs:", refs);
            console.log("status:", status);
            setTotalCount(refs.total);
            setReferrals(refs.items);
            setReferralsStatus(status);            
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        load();
    }, [currentPage]);
    

    function handleReset() {
        currentPage == 0 ? load() : setCurrentPage(0);
    }

    async function sendPayout() {
        try {
            setLoading(true);            
            let answer = await sendUserReferralsWithdraw();
            toast.info(answer.message);
            load();
        } finally {
            setLoading(false);
        }
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

    const bonusType = cashbackTypes.find((item) => item.id === userInfo?.referralType)?.name;
    const domainUrl = window.location.origin;

    return (
        <div className={styles.websiteContainer}>          
        <div className={styles.website}>
        <Header styles={styles} />

        <main>
            <div className={styles.dataBar}>                
                <h3>Реферальная система</h3>
                {userInfo ?
                    (<>
                    <p><b>Ваша реф. ссылка:</b>
                    &nbsp;&nbsp;
                    <a href={`${domainUrl}?rid=1`}>{domainUrl}?rid=1</a>
                    </p>
                    <p>Вы получите бонус в {userInfo.referralPercent}% {bonusType} с каждой сделки. Отправьте ссылку другу.</p>         
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
                    {referralsStatus?.activePayout ?
                    (<>
                        <p>Ваш запрос на выплату обрабатывается оператором.</p>
                        <p>Дата: {referralsStatus.activePayout.dateCreated}</p>
                        <p>Сумма: {referralsStatus.activePayout.amount}</p>
                    </>) :
                    (referralsStatus?.sum - referralsStatus?.payed) > 10 ?
                    <button className={styles.save} style={{ margin: "1em 0", maxHeight: "2.5rem" }} onClick={sendPayout}>Отправить запрос на выплату...</button> :
                    "Можно вывести бонусы, если баланс > 10 USDT"
                    }

                    </>)
                    : <Warning text="Для просмотра рефералов войдите на сайт с логином и паролем" />
                }               
            </div>    
            {userInfo &&
            <div className={styles.dataBar}>                
                <h3>Мои рефералы</h3>
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
                        
                        <button onClick={handleReset}>Применить</button>    
                    </>
                    }
                />
                <Pagination
                    total={totalCount}
                    currentPage={currentPage}
                    rowsPerPage={rowsPerPage}
                    onPageChange={setCurrentPage}
                    styles={styles}
                />

                <Table data={referrals} />

                <Pagination
                    total={totalCount}
                    currentPage={currentPage}
                    rowsPerPage={rowsPerPage}
                    onPageChange={setCurrentPage}
                    styles={styles}
                />
            </div>} 
        </main>           

        <Footer styles={styles} />
        </div>
    </div>
    )
}