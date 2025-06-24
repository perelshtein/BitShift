import React from 'react';
import { Link } from "react-router-dom";
import { ROUTES } from "@/links";
import styles from "@/routes/public/public.module.css";
import { orderStatusList } from "@/routes/Index";

const TableRow = ({ data }) => {
    return (
        <>
            <div>
                <a href={`${ROUTES.ORDER_BY_ID}/${data.id}`}>
                {data.id}
                </a>
            </div>
            <div>
                <a href={`${ROUTES.ORDER_BY_ID}/${data.id}`}>
                {new Date(data.dateCreated).toLocaleString()}
                </a>
            </div>
            <div>{data.give}</div>
            <div>{data.get}</div>
            <div>{orderStatusList.find(it => it.id == data.status)?.name || data.status}</div>
        </>
    );
};

const TableView = ({ data }) => {
    if(data.length == 0) return <div>Нет заявок</div>
    return (
        <div className={styles.tableFiveCol}>
            <div>Номер (id)</div>
            <div>Дата</div>
            <div>Отдаю</div>
            <div>Получаю</div>
            <div>Статус</div>

            {data.map((row, index) => (
                <TableRow key={index} data={row} />
            ))}
        </div>
    );
};

export default TableView;
