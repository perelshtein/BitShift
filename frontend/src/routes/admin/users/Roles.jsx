import { useContext, useState } from "react";
import { toast } from 'react-toastify';
import { Link, useNavigate } from "react-router-dom";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { ROUTES } from "@/links";
import styles from "../admin.module.css";

export default function Roles() {
    const { roles, setRoles, loading, activeRole } = useContext(UsersRolesContext);
    const { deleteRoles } = useContext(AdminAPIContext);
    const [selectedRoles, setSelectedRoles] = useState([]);
    const [deleting, setDeleting] = useState(false);
    const navigate = useNavigate();

    if (loading || !activeRole) {
        return <p>Загрузка...</p>;
    }

    if (deleting) {
        return <p>Удаление...</p>;
    }

    function RolesView() {
        return (
            <ul className={[styles.checkboxList, styles.rolesList].join(' ')}>
                {roles.map((row, index) => (
                    <RolesRow key={index} row={row} />
                ))}
            </ul>
        )
    }

    const RolesRow = ({ row }) => {
        return (
            <li>
                <label>
                    <input type="checkbox" value={row.id} id={row.id} checked={selectedRoles.includes(row.id)}
                        onChange={() => {
                            if (selectedRoles.includes(row.id)) {
                                setSelectedRoles(selectedRoles.filter(it => it !== row.id));
                            } else {
                                setSelectedRoles([...selectedRoles, row.id]);
                            }
                        }} />
                    {row.name}
                </label>
                {activeRole.isEditUserAndRole && <div style={{ marginLeft: "1.5em" }}><Link className={styles.links} to={`${ROUTES.EDIT_ROLE}/${row.id}`}>Редактировать..</Link></div>}
            </li>
        );
    };

    const handleDelete = async () => {
        try {
            setDeleting(true);
            const answer = await deleteRoles({ ids: selectedRoles });
            setSelectedRoles([]);
            setRoles(roles.filter(it => !selectedRoles.includes(it.id)));
            toast.info(answer.message);
        }
        finally {
            setDeleting(false);
        }
    }

    const SelectedActions = ({ rowsSelected }) => {
        return (
            <div className={styles.actionButtons}>
                <p>Выбрано {rowsSelected.length} элементов.</p>
                <button onClick={handleDelete}>Удалить</button>
            </div>
        );
    };

    return (
        <>
            <h2>Роли</h2>
            {activeRole && <button className={styles.addButton} onClick={() => { navigate(ROUTES.EDIT_ROLE) }}>Добавить..</button>}
            <RolesView />
            {selectedRoles.length > 0 && <SelectedActions rowsSelected={selectedRoles} />}
        </>
    );
}