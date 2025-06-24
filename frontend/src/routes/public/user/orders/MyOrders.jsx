import { toast } from "react-toastify";
import { useContext, useState, useEffect } from "react";
import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";
import { AuthContext } from "@/context/AuthContext";
import { CommonAPIContext } from "@/context/CommonAPIContext";
import Header from "@/routes/public/modules/Header";
import Footer from "@/routes/public/modules/Footer";
import styles from "@/routes/public/public.module.css";
import TableView from "./Table";
import Pagination from "./Pagination";
import { Warning } from "@/routes/Index";

export default function MyOrders() {
    const { fetchUserOrders } = useContext(WebsiteAPIContext);
    const [loading, setLoading] = useState(true);
    const [orders, setOrders] = useState([]);
    const rowsPerPage = 10;
    const [currentPage, setCurrentPage] = useState(0);
    const [totalOrdersCount, setTotalOrdersCount] = useState(0);
    const { userInfo } = useContext(AuthContext);

    useEffect(() => {
        const load = async() => {
            try {
                setLoading(true);            
                const start = currentPage * rowsPerPage;
                let ord = (await fetchUserOrders({
                        start: start,
                        count: rowsPerPage
                    })).data;
                setTotalOrdersCount(ord.total);
                setOrders(ord.items.map(it => ({
                    ...it,
                    give: it.amountFrom + " " + it.fromName,
                    get: it.amountTo + " " + it.toName
                })
                ));
            }        
            finally {
                setLoading(false);
            }
        }

        load();        
    }, [currentPage]);

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
                        <h3>Заявки</h3>

                        <Pagination
                            total={totalOrdersCount}
                            currentPage={currentPage}
                            rowsPerPage={rowsPerPage}
                            onPageChange={setCurrentPage}
                            styles={styles}
                        />
                        
                        {userInfo ?
                            <TableView data={orders} />
                            : <Warning text="Для просмотра заявок войдите на сайт с логином и паролем" />
                        }
                        
                        <Pagination
                            total={totalOrdersCount}
                            currentPage={currentPage}
                            rowsPerPage={rowsPerPage}
                            onPageChange={setCurrentPage}
                            styles={styles}
                        />

                    </div>
                </main>
                <Footer styles={styles} />
            </div>
        </div>
    )
}