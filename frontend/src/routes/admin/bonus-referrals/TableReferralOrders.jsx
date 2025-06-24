import { payoutTypes } from "@/routes/Index";
import styles from "@/routes/admin/admin.module.css";

const TableRow = ({ data }) => {
  return (
    <>
      <div>{new Date(data.dateCreated).toLocaleString()}</div>
      <div>{data.userName} | {data.userMail}</div>
      <div>{data.orderId}</div>
      <div>{data.orderValue.toFixed(2)}</div>
      <div>{data.profit.toFixed(2)}</div>
      <div>{data.refName} | {data.refMail}</div>
      <div>{data.earnings.toFixed(2)}</div>
    </>
  );
};

const TableReferralOrders = ({ data }) => {
  if(data.length == 0) return <div>Нет данных</div>
  return (
    <div className={styles.tableSevenCol}>      
        <div>Дата</div>
        <div>Пользов. | почта</div>      
        <div>ID заявки</div>
        <div>Сумма, USDT</div>      
        <div>Прибыль</div>
        <div>Партнер | почта</div>   
        <div>Партнер заработал</div>

      {data.map((row, index) => (
        <TableRow key={index} data={row} />
      ))}
    </div>
  );
};

export default TableReferralOrders;
