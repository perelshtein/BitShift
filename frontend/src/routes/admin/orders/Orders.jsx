import React from 'react';
import { useState, useEffect, useContext } from "react";
import { Link } from "react-router-dom";
import { ROUTES } from "@/links";
import { AdminAPIContext } from '@/context/AdminAPIContext';
import styles from "@/routes/admin/admin.module.css";
import Nested from "@/routes/components/Nested/Nested";
import SelectedActions from './SelectedActions';
import { orderStatusList as status } from '@/routes/Index';
import Bids from './Bids';
import Pagination from '@/routes/components/Pagination';
import { omit } from '@/routes/Index';
import ComboList from './ComboList';

export default function Orders() {
    const { fetchOrders } = useContext(AdminAPIContext);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    // const [updateOrders, setUpdateOrders] = useState(false);
    const [orders, setOrders] = useState([]);
    const [rowsSelected, setRowsSelected] = useState([]);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalOrdersCount, setTotalOrdersCount] = useState(0);
    const rowsPerPage = 10;

    // Get current date and set time to 00:00
    const todayMidnight = new Date();
    todayMidnight.setHours(0, 0, 0, 0); // Sets time to 00:00:00.000
    
    // Adjust for local timezone
    const offset = todayMidnight.getTimezoneOffset() * 60000; // Convert minutes to ms
    const localMidnight = new Date(todayMidnight.getTime() - offset);
    const formattedDate = localMidnight.toISOString().slice(0, 16);

    const [queryOptions, setQueryOptions] = useState({dateStart: sessionStorage.getItem("dateOrdersStart") || formattedDate});
    const [isVisible, setIsVisible] = useState(false);    

    const handleChange = (e) => {
        // Check if e is a synthetic event or custom object
        const name = e?.target?.name || e.name;
        const value = e?.target?.value || e.value;

        setQueryOptions((prev) => ({
            ...prev,
            [name]: value,
        }));
    };

    useEffect(() => {
        sessionStorage.setItem('dateOrdersStart', queryOptions.dateStart);
    }, [queryOptions.dateStart])

    const loadOrders = async () => {
        try {
            setLoading(true);
            const start = currentPage * rowsPerPage;
            const filteredQueryOptions = queryOptions.status === "Все"
                ? omit(queryOptions, "status")
                : queryOptions;

            const answer = (await fetchOrders({
                ...filteredQueryOptions,
                start: start,
                count: rowsPerPage
            })).data;
            setTotalOrdersCount(answer.total);
            setOrders(answer.items);
        }
        finally {
            setLoading(false);
        }
    }

    const handleReset = () => {
        currentPage == 0 ? loadOrders() : setCurrentPage(0);                
    }

    useEffect(() => {        
        loadOrders();
    }, [currentPage]);

    if (loading) {
        return <p>Загрузка...</p>
    }

    if (saving) {
        return <p>Сохранение...</p>
    }

    return (
        <>
            <h2>Заявки</h2>
            <Nested title="Фильтр" isVisible={isVisible} setIsVisible={setIsVisible} styles={styles}
                child={
                    <div className={styles.tableOrders}>
                        <div>
                            <label>ID:
                                <input type="number" value={queryOptions?.id} name="id" onChange={handleChange} />
                            </label>                            
                        </div>


                        <div>
                            <label>
                                Дата от: <input type="datetime-local" value={queryOptions?.dateStart} name="dateStart" onChange={handleChange} />
                            </label>
                            <label>
                                Дата до: <input type="datetime-local" value={queryOptions?.dateEnd} name="dateEnd" onChange={handleChange} />
                            </label>
                        </div>


                        <label>Статус:
                            <ComboList name="status" rows={["Все", ...status]} selectedId={queryOptions?.status} setSelectedId={handleChange} />                            
                        </label>

                        <label>Имя, почта, кошелек, реквизиты:
                            <input name="filter" value={queryOptions?.filter} onChange={handleChange} />                            
                        </label>
                        
                        <button onClick={handleReset}>Применить фильтр</button>

                    </div>} />

            {rowsSelected.length > 0 && <SelectedActions rowsSelected={rowsSelected} setRowsSelected={setRowsSelected}
                status={status} setSaving={setSaving} loadOrders={loadOrders} />}

            <Pagination
                total={totalOrdersCount}
                currentPage={currentPage}
                rowsPerPage={rowsPerPage}
                onPageChange={setCurrentPage}
                className={styles.pagination}
            />
            <Bids bids={orders} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} dateStart={queryOptions.dateStart} />
            <Pagination
                total={totalOrdersCount}
                currentPage={currentPage}
                rowsPerPage={rowsPerPage}
                onPageChange={setCurrentPage}
                className={styles.pagination}
            />
            <p>Найдено заявок: {totalOrdersCount}</p>
        </>
    )
}