import React from 'react';
import { Link } from "react-router-dom";
import { ROUTES } from "@/links";
import SelectAllToggle from "@/routes/components/SelectAllToggle";
import styles from "../admin.module.css";

const TableRow = ({ data, rowsSelected, setRowsSelected }) => {
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
          <label htmlFor={data.id}>{data.name}</label>          
            <Link className={styles.links} to={`${ROUTES.EDIT_CURRENCY_FIELD}/${data.id}`}>Редактировать..</Link>          
        </span>
      </div>      
      <div>{data.status}</div>
    </>
  );
};

const TableView = ({ data, rowsSelected, setRowsSelected }) => {  

  return (
    <div className={styles.tableTwoCol}>     
      <div>Название</div>
      <div>Статус</div>

      {data.map((row, index) => (
        <TableRow key={index} data={row} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
      ))}

      <SelectAllToggle items={data} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
    </div>
  );
};

export default TableView;
