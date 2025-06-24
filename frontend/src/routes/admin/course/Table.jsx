import React from 'react';
import { Link } from "react-router-dom";
import { ROUTES } from "@/links";
import styles from "../admin.module.css";

const TableRow = ({ data }) => {
  let spread = 0;
  if(data.sell != 0 && data.buy != 0) {
    spread = (data.sell - data.buy) / data.buy * 100 // в процентах
    if (Math.abs(spread) < 0.01) {
      spread = spread.toFixed(6);
    } else {
        spread = spread.toFixed(2);
    }
    spread += " %";
  }

  return (
    <>
      <div>{data.from}</div>  
      <div>{data.to}</div>
      <div>{data.tag}</div>
      <div>{data.buy || data.price}</div>
      <div>{spread}</div>      
    </>
  );
};

const TableView = ({ data }) => {  
  return (
    <div className={styles.tableFiveCol}>     
      <div>Отдаю</div>
      <div>Получаю</div>
      <div>Формула</div>
      <div>Курс</div>
      <div>Спред</div>

      {data.map((row, index) => (
        <TableRow key={index} data={row} />
      ))}
    </div>
  );
};

export default TableView;
