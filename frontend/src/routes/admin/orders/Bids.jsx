import React from 'react';
import { useContext } from "react";
import styles from "../admin.module.css";
import { Link, useNavigate } from "react-router-dom";
import { ROUTES } from "@/links";
import { orderStatusList as statusNames } from '@/routes/Index';

export default function Bids({ bids, rowsSelected, setRowsSelected }) {    
    const navigate = useNavigate();   

    return(
        <>
        {bids.map((row) => (
            <React.Fragment key={row.id}>
            <label className={styles.labelBid}> 
                <input type="checkbox" 
                    id={row.id} checked={rowsSelected.includes(row.id)}
                    onChange={() => {
                        if (rowsSelected.includes(row.id)) {
                        setRowsSelected(rowsSelected.filter(it => it !== row.id));
                        } else {
                        setRowsSelected([...rowsSelected, row.id]);
                        }
                    }}
                />
                ID: {row.id}
            </label>
            <div className={styles.tableBidLayout}>
                <div>
                    <p><span className={styles[`order-${row.status}`] || ""}>{statusNames.find(s => s.id === row.status)?.name || row.status}</span></p>
                    <p><b>Дата создания:</b> {new Date(row.dateCreated).toLocaleString()}</p>
                    <p><b>Дата изменения:</b> {new Date(row.dateUpdated).toLocaleString()}</p>
                    <p><b>Курс:</b> {row.course}</p>
                    {row.profit != 0.00 && <p><b>Прибыль:</b> {row.profit} USDT / {(row.profit / (row.from.amount * row.rateGive) * 100).toFixed(2)}%</p>}
                    {row.referral ? <p><b>Реферал:</b> {row.referral}</p>: ""}
                </div>

                <div>
                    <p><b>Отдаю:</b> {row.from.name}</p>
                    <p><b>Сумма:</b> {row.from.amount}</p>
                    <p><b>Со счета:</b> {row.walletFrom}</p>  
                    {row.isManualGive && (
                        <p><b>Ручной курс:</b> {row.rateGive}</p>
                    )}
                    <AdditionalFields fields={row.fieldsGive} />
                </div>

                <div>
                    <p><b>Получаю:</b> {row.to.name}</p>
                    <p><b>Сумма:</b> {row.to.amount}</p>
                    <p><b>На счет:</b> {row.walletTo}</p>
                    {row.isManualGet && (
                        <p><b>Ручной курс:</b> {row.rateGet}</p>
                    )}
                    {row.amlRisk ? <p><b>AML риск:</b> {row.amlRisk}</p> : ""}                    
                    <AdditionalFields fields={row.fieldsGet} />
                </div>

                <div>
                    <p><b>Пользователь:</b> {row.userName}</p>
                    <p><b>Почта:</b> {row.userMail}</p>
                    {row.requisites ? <p><b>Реквизиты:</b> {row.requisites}</p> : ""}
                </div>

                <div className={[styles.spanFourColumns, styles.tableBidBottom].join(' ')}>
                    <button onClick={() => navigate(`${ROUTES.EDIT_ORDER}/${row.id}`)}>Редактировать..</button>
                    <button onClick={ () => {                        
                        navigate(
                            `${ROUTES.ORDER_LOG}/${row.id}`,
                            { state: { statusHistory: row.statusHistory } }
                        )
                    } }>Лог заявки..</button>
                </div>
            </div>            
            </React.Fragment>
        ))}
        </>
    )
}

function AdditionalFields( { fields }) {
    if(!fields || Object.keys(fields).length === 0) return null;
    return (
        <>
          {Object.entries(fields).map(([key, value]) => (
            <p key={key}>
              <b>{key}:</b> {value}
            </p>
          ))}
        </>
      );
}