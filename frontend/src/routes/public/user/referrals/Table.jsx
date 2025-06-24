import React from 'react';
import { Link } from "react-router-dom";
import { ROUTES } from "@/links";
import styles from "@/routes/public/public.module.css";
const TableRow = ({ data }) => {
    return (
        <>
            <div>{new Date(data.dateCreated).toLocaleString()}</div>
            <div>{data.userName} | {data.userMail}</div>
            <div>{data.fromName} ({data.fromCode}) | {data.amountFrom}</div>
            <div>{data.toName} ({data.toCode}) | {data.amountTo}</div>
            <div>{data.earnings}</div>
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
            <div>Отдаю | Сумма</div>            
            <div>Получаю | Сумма</div>
            <div>Ваш бонус</div>

            {data.map((row, index) => (
                <TableRow key={index} data={row} />
            ))}
        </div>
    );
};

export default Table;
