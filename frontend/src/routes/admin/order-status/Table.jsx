import { useContext } from 'react';
import { Link } from "react-router-dom";
import { ROUTES } from "@/links";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import styles from "../admin.module.css";

const TableRow = ({ data, rowsSelected, setRowsSelected, activeRole }) => {
  return (
    <>      
      <div className={styles.checkboxContainer}>
        <input type="checkbox" id={data.id} checked={rowsSelected.includes(data.id)}
          onChange={() => {
            if (rowsSelected.includes(data.id)) {
              setRowsSelected(rowsSelected.filter(it => it !== data.id));
            } else {
              setRowsSelected([...rowsSelected, data.id]);
            }
          }} />
        <span>
          <label htmlFor={data.name}>{data.name}</label>
          <p className={styles.links}>
            <Link to={`${ROUTES.EDIT_ORDER_STATUS}/${data.id}`}>{activeRole?.isEditDirection ? "Редактировать.." : "Просмотреть.."}</Link>
          </p>
        </span>
      </div>      
      <div>{data.id}</div>
      <div>{new Date(data.lastUpdated).toLocaleString()}</div>      
    </>
  );
};

const TableView = ({ data, rowsSelected, setRowsSelected }) => {
  const { activeRole } = useContext(UsersRolesContext);
  return (
    <div className={styles.tableThreeCol}>     
      <div>Название</div>      
      <div>ID</div>
      <div>Обновлено</div>

      {data.map((row, index) => (
        <TableRow key={index} data={row} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} activeRole={activeRole} />
      ))}
    </div>
  );
};

export default TableView;
