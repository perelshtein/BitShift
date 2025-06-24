import { useContext, useEffect, useState } from "react";
import styles from "../admin.module.css";
import { AdminAPIContext } from "@/context/AdminAPIContext";
import Nested from "@/routes/components/Nested/Nested";
import Pagination from "@/routes/components/Pagination";
import TableUserOrders from "./TableUserOrders";

export default function BonusUserOrders() {
    const [loading, setLoading] = useState(true);
    const [totalCnt, setTotalCnt] = useState(0);
    const [orders, setOrders] = useState();
    const rowsPerPage = 10;
    const [currentPage, setCurrentPage] = useState(0);
    const [email, setEmail] = useState();
    const [dateStart, setDateStart] = useState();
    const [dateEnd, setDateEnd] = useState();
    const { fetchCashbackOrders } = useContext(AdminAPIContext);
    const [isVisible, setIsVisible] = useState(false);

    async function load() {
        try {
            setLoading(true);
            const start = currentPage * rowsPerPage;
            let ords = (await fetchCashbackOrders({start: start, count: rowsPerPage, userMail: email,
                dateStart: dateStart?.concat("T00:00"), dateEnd: dateEnd?.concat("T00:00")})).data;
            setOrders(ords.items);
            setTotalCnt(ords.total);
        } finally {
            setLoading(false);
        }
    }

    function handleReset() {
        currentPage == 0 ? load() : setCurrentPage(0);
    }

    useEffect(() => {
        load();
    }, [currentPage]);

    if (loading) {
        return <p>Загрузка...</p>;
    }

    return(
    <>
        <h2>Заявки с бонусами</h2>
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
            total={totalCnt}
            currentPage={currentPage}
            rowsPerPage={rowsPerPage}
            onPageChange={setCurrentPage}
            styles={styles}
        />
        
        <TableUserOrders data={orders} />
        
        <Pagination
            total={totalCnt}
            currentPage={currentPage}
            rowsPerPage={rowsPerPage}
            onPageChange={setCurrentPage}
            styles={styles}
        />
    </>
    )
}