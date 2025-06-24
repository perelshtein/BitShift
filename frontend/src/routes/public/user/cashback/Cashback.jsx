import { useEffect, useState, useContext } from "react";
import styles from "@/routes/public/public.module.css";
import Header from "@/routes/public/modules/Header";
import Footer from "../../modules/Footer";
import { AuthContext } from "@/context/AuthContext";
import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";
import { cashbackTypes } from "@/routes/Index";
import Warning from "@/routes/admin/Warning";
import Table from "./Table";
import Pagination from "@/routes/components/Pagination";
import Nested from "@/routes/components/Nested/Nested";
import { toast } from "react-toastify";

export default function Cashback() {
    const [loading, setLoading] = useState(true);
    const [cashbackStatus, setCashbackStatus] = useState();
    const [referrals, setReferrals] = useState();
    const [ totalCount, setTotalCount ] = useState();
    const [dateStart, setDateStart] = useState();
    const [dateEnd, setDateEnd] = useState();
    const [isVisible, setIsVisible] = useState(false);
    const [currentPage, setCurrentPage] = useState(0);
    const rowsPerPage = 10;
    const { loggedIn, userInfo } = useContext(AuthContext);
    const { fetchUserCashbackStatus, fetchUserCashback, sendUserCashbackWithdraw } = useContext(WebsiteAPIContext);

    async function load() {
        try {
            setLoading(true);
            const start = currentPage * rowsPerPage;
            const [status, ord] = await Promise.all([
                fetchUserCashbackStatus().then(res => res.data),
                fetchUserCashback({start: start, count: rowsPerPage, dateStart: dateStart?.concat("T00:00"),
                    dateEnd: dateEnd?.concat("T00:00")}).then(res => res.data)
            ]);                

            setTotalCount(ord.total);
            setReferrals(ord.items);
            setCashbackStatus(status);
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
            let answer = await sendUserCashbackWithdraw();         
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

    return (
        <div className={styles.websiteContainer}>          
        <div className={styles.website}>
        <Header styles={styles} />

        <main>
            <div className={styles.dataBar}>                
                <h3>Кэшбек</h3>
                {userInfo ?
                    (<>                    
                    <p>Вы получите бонус в {userInfo.cashbackPercent}% {bonusType} с каждого обмена.</p>
                    <p><b>Всего начислено:</b>
                        &nbsp;&nbsp;
                        {cashbackStatus?.sum?.toFixed(2) || 0.0} USDT
                    </p>
                    <p><b>Всего выплачено:</b>
                        &nbsp;&nbsp;
                        {cashbackStatus?.payed?.toFixed(2) || 0.0} USDT
                    </p>
                    <p><b>Баланс:</b>
                        &nbsp;&nbsp;
                        {cashbackStatus?.sum - cashbackStatus?.payed || 0.0} USDT
                    </p>
                    {cashbackStatus?.activePayout ?
                    (<>
                        <p>Ваш запрос на выплату обрабатывается оператором.</p>
                        <p>Дата: {cashbackStatus.activePayout.dateCreated}</p>
                        <p>Сумма: {cashbackStatus.activePayout.amount}</p>
                    </>) :
                    (cashbackStatus?.sum - cashbackStatus?.payed) > 10 ?
                    <button className={styles.save} style={{ margin: "1em 0", maxHeight: "2.5rem" }} onClick={sendPayout}>Вывести бонусы...</button> :
                    "Можно вывести бонусы, если баланс > 10 USDT"
                    }

                    </>)
                    : <Warning text="Для просмотра кэшбека войдите на сайт с логином и паролем" />
                }               
            </div>    
            {userInfo &&
            <div className={styles.dataBar}>                
                <h3>Мои заявки с кэшбеком</h3>
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