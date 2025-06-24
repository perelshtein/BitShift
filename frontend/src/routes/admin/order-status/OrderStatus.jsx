import { useState, useEffect, useContext } from "react";
import { useNavigate } from "react-router-dom";
import { ROUTES } from "@/links";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import TableView from "./Table";
import { toast } from 'react-toastify';
import styles from "../admin.module.css";

export default function OrderStatus() {   
    const { fetchOrderStatuses, deleteOrderStatuses } = useContext(AdminAPIContext);
    const { activeRole, loading: rolesLoading } = useContext(UsersRolesContext);
    const [orderStatuses, setOrderStatuses] = useState();
    const [rowsSelected, setRowsSelected ] = useState([]);
    const [loading, setLoading] = useState(true);
    const [deleting, setDeleting] = useState(false);
    const navigate = useNavigate();    

    useEffect(() => {
        const loadStatuses = async() => {
            setLoading(true);
            try {
                let answer = await fetchOrderStatuses();
                setOrderStatuses(answer.data);
            } finally {
                setLoading(false);
            }
        }
        loadStatuses();
    }, []);

    const handleDelete = async() => {
        try {
            setDeleting(true);
            const answer = await deleteOrderStatuses({ids: rowsSelected});
            setOrderStatuses([ ...orderStatuses.filter(it => !rowsSelected.includes(it.id)) ]);
            setRowsSelected([]);
            toast.info(answer.message);
        }
        finally {
            setDeleting(false);
        }
    }

    const SelectedActions = () => {
        return (
          <div className={styles.actionButtons}>      
            <p>Выбрано {rowsSelected.length} элементов.</p>
            <button onClick={handleDelete}>Удалить</button>
          </div>
        );
      };

    if(rolesLoading || loading) {
        return <p>Загрузка...</p>
    }

    if(deleting) {    
        return <p>Удаление...</p>
    }


    return(
        <>
        <h2>Статусы заявок</h2>
        {activeRole.isEditDirection && (
            <button className={styles.addButton} onClick={() => { navigate(ROUTES.EDIT_ORDER_STATUS); }}>Добавить..</button>
        )}

        <TableView data={orderStatuses} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
        {rowsSelected.length > 0 && activeRole.isEditDirection && (
            <SelectedActions rowsSelected={rowsSelected} />
        )}
        </>
    )
}