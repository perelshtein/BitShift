import styles from "../admin.module.css";
import { Link } from "react-router-dom";
import { ROUTES } from "@/links";
import { reviewStatusList } from "@/routes/Index";
import StarRating from "@/routes/components/StarRating/StarRating";

const TableRow = ({ data: row, rowsSelected, setRowsSelected }) => {
    return (
      <>
        <div className={styles.checkboxContainer}>
          <input id={row.id} type="checkbox" checked={rowsSelected.includes(row.id)}
            onChange={() => {
              if (rowsSelected.includes(row.id)) {
                setRowsSelected(rowsSelected.filter(it => it !== row.id));
              } else {
                setRowsSelected([...rowsSelected, row.id]);
              }
            }} />
            <span>
              <label htmlFor={row.id}>
                {new Date(row.date).toLocaleString()}
              </label>
              <p className={styles.links}>
                <Link to={`${ROUTES.EDIT_REVIEW}/${row.id}`}>Редактировать..</Link>
              </p>
            </span>
        </div>  
        <div>{row.caption} | {row.mail}</div>
        <div>{row.text}</div>
        <div>
          <StarRating value={row?.rating} size={20} disabled />
          <div style={{paddingTop: 0}}>{reviewStatusList.find(it => it.id == row.status)?.name || row.status}</div>
        </div>
      </>
    );
};
  
const TableReviews = ({ data, rowsSelected, setRowsSelected }) => {
    return (
      <div className={styles.tableFourCol}>
        <div>Дата</div>
        <div>Имя | Почта</div>
        <div>Текст</div>
        <div>Оценка | Статус</div>
  
        {data?.map((row) => (
          <TableRow key={row.id} data={row} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
        ))}
      </div>
    );
};

export default TableReviews;