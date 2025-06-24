import { useParams, useNavigate } from "react-router-dom";
import { useContext, useState, useEffect } from "react";
import { toast } from 'react-toastify';
import CheckboxList from "@/routes/components/CheckboxList";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import styles from "../admin.module.css";

export default function EditCurrencyField() {
    const { id } = useParams();    
    const { currencies, currencyFields, setCurrencyFields, loading: currencyLoading } = useContext(CurrencyContext);
    const { activeRole } = useContext(UsersRolesContext);    
    const { sendCurrencyField, fetchCurrenciesForField, sendCurrenciesForField } = useContext(AdminAPIContext);

    const [fieldData, setFieldData] = useState({name: "", isRequired: false, hintAccountFrom: "", hintAccountTo: ""});
    const [saving, setSaving] = useState(false);
    const [loading, setLoading] = useState(id ? true : false);
    const [currenciesGive, setCurrenciesGive] = useState([]);
    const [currenciesGet, setCurrenciesGet] = useState([]);
    const navigate = useNavigate();
    const field = currencyFields.find(it => it.id == id);

    //загрузим валюты, в которых отображается это поле
    useEffect(() => {
        const loadGiveGetCurrencies = async() => {
          try {            
            let answer = (await fetchCurrenciesForField({id: id})).data;
            setCurrenciesGive(answer.give);
            setCurrenciesGet(answer.get);
          }
          finally {
            setLoading(false);
          }
        }
        const setInputText = () => {
          setFieldData({
            name: field?.name || "",
            isRequired: field?.isRequired || false,
            hintAccountFrom: field?.hintAccountFrom || "",
            hintAccountTo: field?.hintAccountTo || "",
          })          
        }
        if(id) {
          loadGiveGetCurrencies();
          setInputText();
        }
      }, [currencyLoading, id]);
    
    //редакт флага и полей (кроме Отдаю и Получаю)
    const handleChange = (e) => {
        const { name, type, checked, value } = e.target;
        setFieldData((prev) => ({
          ...prev,
          [name]: type === "checkbox" ? checked : value, // Use `checked` for checkboxes
        }));
      };     

    const handleSave = async() => {
        try {            
            setSaving(true);
            let updatedField = {
                ...fieldData,
                ...(parseInt(id) ? { id: parseInt(id) } : {}) //если нет id, его не отправляем          
            };
            const answer = (await sendCurrencyField({field: updatedField})).data;            
            
            setCurrencyFields(prevFields => {
                const updatedFields = [...prevFields];
                const index = prevFields.findIndex((r) => r.id == updatedField.id);
                if (index != -1) {
                  updatedFields[index] = updatedField;
                } else {                 
                  updatedFields.push({ ...updatedField, id: answer.id });
                }              
                return updatedFields;
            });
            await sendCurrenciesForField({id: answer.id, giveCurrencies: currenciesGive, getCurrencies: currenciesGet});
            // console.log("give:", currenciesGive);
            // console.log("get:", currenciesGet);
            toast.info(answer.message);
            navigate(-1);
        }
        finally {
            setSaving(false);
        }
    };

    if(loading || currencyLoading || !activeRole) {
        return <p>Загрузка...</p>
    }
    
    if(saving) {
        return <p>Сохранение...</p>
    }
    
    const firstWord = activeRole.isEditCurrency ? "Редактировать" : "Просмотреть";
    return (
    <>    
        {id ? <h2>{firstWord} поле - { field.name }</h2> : <h2>Создать поле</h2>}
        
        <div className={styles.tableTwoColLayout}>
        Название: <input name="name" value={fieldData.name} onChange={handleChange} />
        <label className={[styles.spanTwoColumns, styles.oneLine].join(" ")}>
            <input type="checkbox" name="isRequired" checked={fieldData.isRequired} onChange={handleChange} />
            Обязательное поле
        </label>
        Подсказка Со счета: <input name="hintAccountFrom" value={fieldData.hintAccountFrom} onChange={handleChange} />
        Подсказка На счет: <input name="hintAccountTo" value={fieldData.hintAccountTo} onChange={handleChange} />
        Валюты Отдаю:
        <CheckboxList
          rows={currencies}
          checkedKeys={currenciesGive}
          onCheckChange={setCurrenciesGive}
          className={styles.checkboxList}
        />
        Валюты Получаю:
        <CheckboxList
          rows={currencies}
          checkedKeys={currenciesGet}
          onCheckChange={setCurrenciesGet}
          className={styles.checkboxList}
        />
        </div>          
        
        <div className={styles.actionButtons}>
            {activeRole.isEditCurrency && <button id="save" onClick={handleSave}>Сохранить</button>}
            <button onClick={() => navigate(-1)}>Отмена</button>
        </div>
    </>
    )
}