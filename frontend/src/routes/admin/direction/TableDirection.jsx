import React from 'react';
import { Link } from "react-router-dom";
import { ROUTES } from "@/links.js";
import { useContext } from 'react';
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import SelectAllToggle from "@/routes/components/SelectAllToggle.jsx";
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
          <label htmlFor={data.id}>{data.from.name} -&gt; {data.to.name}</label>
          <p className={styles.links}>
            <Link to={`${ROUTES.EDIT_DIRECTION}/${data.id}`}>{activeRole?.isEditDirection ? "Редактировать.." : "Просмотреть.."}</Link>
          </p>
        </span>
      </div>      
      <div>{data.id}</div>
      <div>{data.price}</div>
      <div>{data.isActive ? "Активно" : "Отключено"}</div>
    </>
  );
};

const TableView = ({ data, rowsSelected, setRowsSelected }) => {  
  const { activeRole } = useContext(UsersRolesContext);
  return (
    <div className={styles.tableFourCol}>     
      <div>Название</div>
      <div>ID</div>
      <div>Курс</div>
      <div>Статус</div>

      {data.map((row, index) => (
        <TableRow key={index} data={row} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} activeRole={activeRole} />
      ))}
      <SelectAllToggle items={data} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
    </div>
  );
};

export default TableView;
