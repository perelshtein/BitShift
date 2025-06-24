import { useContext, useEffect, useState } from "react";
import { toast } from 'react-toastify';
import { useParams, useNavigate } from "react-router-dom";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import CheckboxList from "@/routes/components/CheckboxList.jsx";
import styles from "../admin.module.css";
import { ROUTES } from "@/links";

export default function EditRole() {
    const { id } = useParams();
    const { roles, setRoles, setActiveRole } = useContext(UsersRolesContext);
    const [saving, setSaving] = useState(false);
    // const [loading, setLoading] = useState(false);
    const { sendRole } = useContext(AdminAPIContext);    
    const [role, setRole] = useState();
    const [checkedPermissions, setCheckedPermissions] = useState([]);
    
    const permissions = [
        { id: "isAdminPanel", name: "Доступ в админку" },
        { id: "isEditUserAndRole", name: "Менять список пользователей и роли" },
        { id: "isEditNews", name: "Добавлять новости" },
        { id: "isEditOptions", name: "Редактировать настройки" },
        { id: "isEditCurrency", name: "Редактировать валюты" },
        { id: "isEditDirection", name: "Редактировать направления обмена" },
        { id: "isEditReserve", name: "Указывать резерв" },
        { id: "isEditNotify", name: "Редактировать уведомления" },
        { id: "isEditReview", name: "Работать с отзывами" },        
        { id: "isSendReferralPayouts", name: "Подтверждать выплаты бонусов" },

        // { id: "isEditOrder", name: "Редактировать заявку" },
        // { id: "isChangeOrderStatus", name: "Менять статус заявки" },        
        // { id: "isMakePayments", name: "Делать выплаты по кнопке" },
        // { id: "isSetRate", name: "Задавать курс" },        
        
    ];   
    
    const navigate = useNavigate();

    useEffect(() => {
        if (!roles) return;
        if (id) {
            const existingRole = roles.find((it) => it.id == id);
            setRole(existingRole);
            const existingPermissions = permissions
                .filter(permission => existingRole?.[permission.id]) // Проверить, есть ли в Role свойство с именем permission.id
                .map(permission => permission.id);
            setCheckedPermissions(existingPermissions);
        }
        else {
            setRole({ name: "" });
        }
    }, [id, roles]);

    if (saving) {
        return <p>Сохранение...</p>;
      }

    if(!role) {
        return <p>Загрузка...</p>
    }

    //с бэка приходит только id и message, остальное у нас есть
    const handleSave = async() => {
        try { 
            setSaving(true);           
            let updatedRole = {
                name: role.name,
                ...(parseInt(id) ? { id: parseInt(id) } : {}) //если нет id, его не отправляем          
            };
            checkedPermissions.forEach(it => updatedRole[it] = true);            
            const answer = await sendRole({role: updatedRole});            

            setRoles((prevRoles) => {
                // Всегда создаем новый массив, иначе React не заметит изменений
                const updatedRoles = [...prevRoles];
              
                const roleIndex = prevRoles.findIndex((r) => r.id == updatedRole.id);
                if (roleIndex != -1) {                    
                  updatedRoles[roleIndex] = updatedRole;
                } else {                 
                  updatedRoles.push({ ...updatedRole, id: answer.data.id });
                }
                
                // чтобы не заходить снова в админку, применим опции для активной роли
                const activeRoleId = localStorage.getItem("activeRoleId"); 
                const updatedActiveRole = updatedRoles.find((role) => role.id === parseInt(activeRoleId));
                setActiveRole(updatedActiveRole);
                
                return updatedRoles;
              });

            toast.info(answer.message);
            navigate(ROUTES.ROLES);
        }
        finally {
            setSaving(false);
        }
    };

    return (
        <>
            <Caption id={id} roleName={role.name} />
            <label>
                Название роли:
                <input value={role.name} onChange={(e) => setRole({...role, name: e.target.value})} />
            </label>
            <p>Права:</p>
            <CheckboxList
                rows={permissions}
                checkedKeys={checkedPermissions}
                onCheckChange={setCheckedPermissions}
                className={styles.checkboxList}
            />
            <button className={styles.save} onClick={handleSave}>
                Сохранить
            </button>
        </>
    );
}

function Caption({ id, roleName }) {
    return id ? <h2>Редактировать роль - {roleName}</h2> : <h2>Создать роль</h2>;
}
