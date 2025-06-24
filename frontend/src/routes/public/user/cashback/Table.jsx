import React from 'react';
import { Link } from "react-router-dom";
import { ROUTES } from "@/links";
import styles from "@/routes/public/public.module.css";
const TableRow = ({ data }) => {
    return (
        <>
            <div>{new Date(data.dateCreated).toLocaleString()}</div>
            <div>{data.userName} | {data.userMail}</div>
            <div>{data.amountFrom} {data.fromName} ({data.fromCode})</div>
            <div>{data.amountTo} {data.toName} ({data.toCode})</div>
            <div>{data.earnings.toFixed(2)}</div>
        </>
    );
};

const Table = ({ data }) => {
    if(data.length == 0) return (        
        <p>Нет обменов</p>        
    )
    return (
        <div className={styles.tableFiveCol}>
            <div>Дата</div>
            <div>Имя | Почта</div>
            <div>Отдаю</div>            
            <div>Получаю</div>
            <div>Ваш бонус, USDT</div>

            {data.map((row, index) => (
                <TableRow key={index} data={row} />
            ))}
        </div>
    );
};

export default Table;
