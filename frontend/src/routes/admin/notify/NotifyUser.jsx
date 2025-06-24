import { toast } from "react-toastify";
import { useState, useContext, useEffect } from "react";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import ComboList from "@/routes/components/ComboList";
import styles from "../admin.module.css";
import { orderStatusList } from "@/routes/Index";

export default function NotifyUser() {
    const { fetchUsersNotify, sendUsersNotify } = useContext(AdminAPIContext);
    const { activeRole, loading: rolesLoading } = useContext(UsersRolesContext);     

    const [subject, setSubject] = useState("");
    const [text, setText] = useState("");
    const [selectedStatus, setSelectedStatus] = useState("new");      
    const [notify, setNotify] = useState();

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    const statusList = [
        ...orderStatusList,        
        { id: "newUser", name: "Регистрация на сайте" },
        { id: "registrationSuccess", name: "Регистрация завершена" },
      ];

    const handleChange = (key, prop, value) => {                
        setNotify(prev => ({
          ...prev,
          [key]: {
            ...prev[key],
            text: prop === "text" ? value : prev[key]?.text || "", 
            subject: prop === "subject" ? value : prev[key]?.subject || ""
          }
        }));
        prop === "text" ? setText(value) : setSubject(value);
      };

    const handleSave = async() => {
        setSaving(true);
        try {
            let result = await sendUsersNotify({notify});
            toast.info(result.message);
        }
        finally {
            setSaving(false);
        }
    }

    useEffect(() => {
        const loadData = async() => {
            try {
                setLoading(true);
                let answer = (await fetchUsersNotify()).data;
                setNotify(answer);
                setText(answer?.[selectedStatus]?.text || "");
                setSubject(answer?.[selectedStatus]?.subject || "");
            }
            finally {
                setLoading(false);
            }
        }
        loadData();    
    }, []);

    useEffect(() => {        
        console.log(selectedStatus);
        const selectedItem = notify?.[selectedStatus];
        setSubject(selectedItem?.subject || "");
        setText(selectedItem?.text || "");
    }, [selectedStatus]);

    if(loading || rolesLoading) {
        return(
            <p>Загрузка...</p>
        )
    }

    if(saving) {
        return(
            <p>Сохранение...</p>
        )
    }

    return(
        <>
        <h2>Уведомления пользователям</h2>
        <label>
            Статус заявки
            <ComboList rows={statusList} selectedId={selectedStatus} setSelectedId={setSelectedStatus} />
        </label>

        <div>
            <input placeholder="Тема письма" value={subject} onChange={e => handleChange(selectedStatus, "subject", e.target.value)} />
        </div>

        <div>
            <textarea rows="8" cols="30" style={{width: "50%"}} value={text} onChange={e => handleChange(selectedStatus, "text", e.target.value)} />
        </div>

        <div>Можно использовать теги в квадратных скобках, например:
        <ul>
            <li>[register] - ссылка для регистрации со случайным кодом;</li>
            <li>[login] - имя пользователя (почта);</li>
            <li>[password] - пароль после успешной регистрации;</li>
            <li>[id] - номер заявки;</li>
            <li>[requisites] - реквизиты (для ручных направлений);</li>
            <li>[email] - почта пользователя;</li>
            <li>[walletFrom] - кошелек Отдаю;</li>
            <li>[walletTo] - кошелек Получаю;</li>
            <li>[amountFrom] - колич.валюты Отдаю;</li>
            <li>[amountTo] - колич валюты Получаю;</li>
            <li>[receiveType] - модуль приема: Ручной (оператор, рубли) / Авто (криптовалюта);</li>
            <li>[sendType] - модуль отправки: Ручной / Авто;</li>
            <li>[currencyFrom] - валюта Отдаю;</li>
            <li>[currencyTo] - валюта Получаю.</li>
        </ul>
        </div>

        {activeRole.isEditNotify && <button className={styles.save} onClick={handleSave}>Сохранить</button>}

        </>
    )
}