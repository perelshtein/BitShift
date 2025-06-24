import { toast } from "react-toastify";
import { useState, useContext, useEffect } from "react";
import { useParams } from "react-router-dom";
import CheckboxList from "@/routes/components/CheckboxList.jsx";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import ComboList from "@/routes/components/ComboList.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { CommonAPIContext } from "@/context/CommonAPIContext.jsx";
import SelectAllToggle from "@/routes/components/SelectAllToggle.jsx";
import styles from "../admin.module.css";

export default function EditOrderStatus() {
    const { currencies, loading: currenciesLoading } = useContext(CurrencyContext);    
    const { fetchOrderStatus } = useContext(CommonAPIContext);
    const { sendOrderStatus } = useContext(AdminAPIContext);
    const { activeRole, loading: rolesLoading } = useContext(UsersRolesContext); 
    const [currenciesGive, setCurrenciesGive] = useState([]);
    const [currenciesGet, setCurrenciesGet] = useState([]);    
    const [selectedStatus, setSelectedStatus] = useState("popup");

    const bidStatusList = [
        { id: "popup", name: "Всплывающее окно" },
        { id: "deadlines", name: "Сроки выполнения" },
        { id: "instructions", name: "Инструкции по обмену" },
        { id: "new", name: "Новая" },
        { id: "waitingForPayment", name: "Ожидание оплаты" },
        { id: "waitingForConfirmation", name: "Ждем подтверждения оплаты" },
        { id: "payed", name: "Оплачено" },
        { id: "waitingForPayout", name: "Ждем подтверждения выплаты" },
        { id: "onCheck", name: "На проверке" },
        { id: "completed", name: "Выполнено" },
        { id: "cancelled", name: "Отмена" },
        { id: "error", name: "Ошибка" },
        { id: "deleted", name: "Удаленная" }
    ]
    const [orderStatus, setOrderStatus] = useState({name: "", list: bidStatusList});
    const [statusText, setStatusText] = useState("");
    const { id } = useParams();
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);    

    useEffect(() => {
        const loadOrderStatus = async() => {
            setLoading(true);
            let result = (await fetchOrderStatus({id: id})).data;
            setOrderStatus({
                ...result, 
                list: result.list.map(e => ({
                    id: e.statusType,
                    name: bidStatusList.find(it => it.id === e.statusType).name,
                    text: e.text
                }))
            });
            setCurrenciesGive(result.idsFrom);
            setCurrenciesGet(result.idsTo);
            setSelectedStatus("popup");            
            setStatusText(result.list?.find(it => it.statusType === "popup")?.text || "");
            setLoading(false);            
        }
        if(id) {
            loadOrderStatus();            
        }
    }, [id]);

    useEffect(() => {        
        const selectedItem = orderStatus?.list?.find(it => it.id === selectedStatus);
        setStatusText(selectedItem?.text || "");
    }, [selectedStatus]);

    const handleChange = (e) => {
        const { name, type, checked, value } = e.target;
        setOrderStatus(prev => ({
          ...prev,
          [name]: type === "checkbox" ? checked : value, // Use `checked` for checkboxes
        }));
    };

    const handleTextChange = (e) => {
        setStatusText(e.target.value);
        setOrderStatus(prev => ({
            ...prev, 
            list: prev.list.map(it =>
                it.id === selectedStatus
                ? {...it, text: e.target.value}                
                : it),
        }));
    };

    const handleSave = async() => {
        try {
            setSaving(true);            
            let updated = {
                ...orderStatus, 
                list: orderStatus.list.map(e => ({ statusType: e.id, text: e.text || "" }))
            };
            
            updated.idsFrom = currenciesGive;
            updated.idsTo = currenciesGet;
            const answer = await sendOrderStatus({orderStatus: updated});
            toast.info(answer.message);
        }
        finally {
            setSaving(false);
        }
    }

    if(currenciesLoading || rolesLoading || loading) {
        return <p>Загрузка...</p>
    }

    if(saving) {
        return <p>Сохранение...</p>
    }

    return(
        <>
        <h2>{activeRole.isEditDirection ? (id ? "Редактировать" : "Создать") : "Просмотреть"} группу статусов</h2>   
        <div>
        <label>
            Название:
            <input name="name" value={orderStatus?.name} onChange={handleChange} />
        </label>
        </div>  

        <label>
        Статус заявки: <ComboList rows={orderStatus?.list} selectedId={selectedStatus} setSelectedId={setSelectedStatus} />
        </label>
        <textarea rows="10" cols="40" value={statusText} onChange={handleTextChange} style={{width: "100%"}} />
    
        <div>Применить для валют:</div>
        <div className={styles.tableTwoColLayout}>    
        <div>
        <h4 style={{marginTop: "0"}}>Отдаю:</h4>    
            <CheckboxList
                rows={ currencies.map( it => ({id: it.id, name: `${it.name} (${it.code})`})) }
                checkedKeys={ currenciesGive }
                onCheckChange={ setCurrenciesGive }
                className={styles.checkboxList}
            />
            <SelectAllToggle items={ currencies } rowsSelected={ currenciesGive } setRowsSelected={ setCurrenciesGive } />
        </div>    
    
        <div>
        <h4 style={{marginTop: "0"}}>Получаю:</h4>    
            <CheckboxList
                rows={ currencies.map( it => ({id: it.id, name: `${it.name} (${it.code})`})) }
                checkedKeys={ currenciesGet }
                onCheckChange={ setCurrenciesGet }
                className={styles.checkboxList}
            />
            <SelectAllToggle items={ currencies } rowsSelected={ currenciesGet } setRowsSelected={ setCurrenciesGet } />
        </div>    
        </div>
    
        {activeRole.isEditDirection && <button className={styles.save} onClick={handleSave}>Сохранить</button>}
        
        </>
    )
}