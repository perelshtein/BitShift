import { useContext } from "react";
import { Link } from "react-router-dom";
import { ROUTES } from "@/links";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import SelectAllToggle from "@/routes/components/SelectAllToggle";
import styles from "../admin.module.css";

const TableRow = ({ data, rowsSelected, setRowsSelected, isCanEdit, roles, defaultCashback }) => {
  return (
    <>      
      <div className={styles.checkboxContainer}>
        <input type="checkbox" id={data.id} checked={rowsSelected.includes(data.id)} disabled={!isCanEdit}
          onChange={() => {
            if (rowsSelected.includes(data.id)) {
              setRowsSelected(rowsSelected.filter(it => it !== data.id));
            } else {
              setRowsSelected([...rowsSelected, data.id]);
            }
          }} />
        <span>
          <label htmlFor={data.id}>{data.name}</label>
            {isCanEdit && <Link className={styles.links} to={`${ROUTES.EDIT_USER}/${data.id}`}>Редактировать..</Link>}
        </span>
      </div>
      <div>{new Date(data.date).toLocaleString()}</div>
      <div>{data.mail}</div>
      <div>{roles.find(it => it.id == data.roleId).name}</div>
      <div>{data.cashbackPercent || defaultCashback}% </div>      
      <div>
        {data.ordersCount}
      </div>

    </>
  );
};

const TableView = ({ data, rowsSelected, setRowsSelected, defaultCashback }) => {
  const { roles, activeRole } = useContext(UsersRolesContext);  

  return (
    <div className={styles.tableSixCol}>     
      <div>Имя</div>
      <div>Дата регистрации</div>
      <div>Почта</div>
      <div>Роль</div>
      <div>Кэшбек</div>
      <div>Обмены</div>

      {data.map((row, index) => (
        <TableRow key={index} data={row} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} isCanEdit={activeRole.isEditUserAndRole}
          roles={roles} defaultCashback={defaultCashback} />
      ))}
      <SelectAllToggle items={data} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
    </div>
  );
};

export default TableView;
