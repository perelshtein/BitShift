import { useContext, useState, useEffect } from "react";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { toast } from 'react-toastify';
import styles from "../admin.module.css";

const ExchangeOptions = ({exchangeName, isShowApiKey, isShowSecretKey}) => {
    const { fetchExchange, sendExchange, updateCourses } = useContext(AdminAPIContext);
    const [loading, setLoading] = useState(true); 
    const [saving, setSaving] = useState(false);    
    const [updating, setUpdating] = useState(false);
    const [exchange, setExchange] = useState();
    const [blacklistInput, setBlacklistInput] = useState();

    const handleChange = (e) => {
      const { name, type, checked, value } = e.target;
      setExchange((prev) => ({
        ...prev,
        [name]: type === "checkbox" ? checked : value, // Use `checked` for checkboxes
      }));
    };

    const handleSave = async() => {
      try {
        setSaving(true);
        let updated = exchange;          

        if (blacklistInput.trim().length > 0) {
          updated.blacklist = blacklistInput.split(',').map(e => e.trim());
        } else {
            updated.blacklist = []; // явно зададим пустой массив!
        }
        
        const answer = await sendExchange({exchange: updated});
        toast.info(answer.message);
      }
      finally {
        setSaving(false);
      }
    }

    const handleUpdate = async() => {
      try {
        setLoading(true);        
        const answer = await updateCourses({exchangeName: exchangeName});
        setUpdating(true); // для обновл инф о бирже
        toast.info(answer.message);
      }
      finally {
        setLoading(false);
      }
    }

    useEffect(() => {
        const loadExchanges = async() => {
            try {
                const answer = (await fetchExchange({exchangeName: exchangeName})).data;
                setExchange(answer);
                setBlacklistInput(answer.blacklist.join(','));
            }
            finally {
                setLoading(false);
                setUpdating(false);
            }
        }
        
        loadExchanges();
        }, [updating]);

    if(loading) {
        return <p>Загрузка...</p>
    }

    if(saving) {
        return <p>Сохранение...</p>
    }

    return (
        <>
        <h2>{exchangeName}</h2>
        <div className={styles.container}>
            <label>
              <input type="checkbox" name="isEnabled" checked={exchange.isEnabled} onChange={handleChange} />
              Включить
            </label>
            <div className={styles.horzList}>
              URL:
              <input name="url" value={exchange.url} onChange={handleChange} />
            </div>
            <div className={styles.horzList}>
              Черный список валют:       
              <input name="blacklist" placeholder="через запятую" value={blacklistInput} onChange={e => setBlacklistInput(e.target.value)}/>
            </div>
            <div className={styles.horzList}>
              Запрашивать курсы каждые
              <input name="updatePeriod" value={exchange.updatePeriod} onChange={handleChange} type="number" min="1" />
              минут
            </div>
            <div className={styles.horzList}>
              Макс.количество ошибок подряд:
              <input name="maxFailCount" value={exchange.maxFailCount} onChange={handleChange} type="number" min="0" />
            </div>

            Последнее обновление: {new Date(exchange.lastUpdate).toLocaleString()}<br />  
            
            {isShowApiKey && <label className={styles.horzList}>API-ключ: <input name="apiKey" value={exchange.apiKey} onChange={handleChange} 
              autoComplete="off" /></label>}
            {isShowSecretKey && <label className={styles.horzList}>Секретный ключ: <input name="secretKey" value={exchange.secretKey} onChange={handleChange} 
              autoComplete="off" /></label>}

            <div className={styles.actionButtons}>
              <button className={styles.save} onClick={handleSave}>Сохранить</button>
              <button onClick={handleUpdate}>Обновить сейчас</button>
            </div>
        </div>
        </>
    )
}
export default ExchangeOptions;