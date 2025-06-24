import { payoutTypes } from "@/routes/Index";
import styles from "@/routes/admin/admin.module.css";

const TableRow = ({ data, handleConfirm }) => {
  return (
    <>
      <div>{data.id}</div>
      <div>{data.amount}</div>
      <div>
        {payoutTypes.find(it => it.id == data.status)?.name || data.status}
        {data.status == "pending" && <button onClick={() => handleConfirm({rid: data.refId})}>Отметить как завершенный</button>}
      </div>
      <div>{new Date(data.dateCreated).toLocaleString()}</div>              
    </>
  );
};

const TableReferralPayouts = ({ data, handleConfirm }) => {
  if(data.length == 0) return <div>Нет данных</div>
  return (
    <div className={styles.tableFourCol}>      
      <div>Реферал</div>      
      <div>Сумма, USDT</div>      
      <div>Статус</div>
      <div>Дата</div>
      {data.map((row, index) => (
        <TableRow key={index} data={row} handleConfirm={handleConfirm} />
      ))}
    </div>
  );
};

export default TableReferralPayouts;
