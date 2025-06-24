import { ROUTES } from "@/links.js";
import { useState, useContext } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from 'react-toastify';
import TableView from "./Table.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import styles from "../admin.module.css";

export default function CurrencyFields() {
    const [rowsSelected, setRowsSelected] = useState([]);
    const [deleting, setDeleting] = useState(false);
    const navigate = useNavigate();
    const { activeRole } = useContext(UsersRolesContext);
    const { currencyFields, setCurrencyFields, loading } = useContext(CurrencyContext);
    const { deleteCurrencyFields } = useContext(AdminAPIContext);

    const SelectedActions = ({rowsSelected}) => {
        return (
          <div className={styles.actionButtons}>      
            <p>Выбрано {rowsSelected.length} элементов.</p>
            <button onClick={handleDelete}>Удалить</button>
          </div>
        );
    };

    const handleDelete = async() => {
        try {
            setDeleting(true);
            const answer = await deleteCurrencyFields({ids: rowsSelected});
            setCurrencyFields([ ...currencyFields.filter(it => !rowsSelected.includes(it.id)) ])
            setRowsSelected([]);
            toast.info(answer.message);
        }
        finally {
            setDeleting(false);
        }
    }

    if(deleting) {
        return <p>Удаление...</p>
    }


    if(loading || !activeRole) {
        return <p>Загрузка...</p>
      }

    return (
        <>
            <h2>Поля валют</h2>
            {activeRole.isEditCurrency && <button className={styles.addButton} onClick={() => { navigate(ROUTES.EDIT_CURRENCY_FIELD); }}>Добавить..</button>}
            <TableView data={currencyFields} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
            { rowsSelected.length > 0 && <SelectedActions rowsSelected={rowsSelected} /> }
        </>
    )
}
