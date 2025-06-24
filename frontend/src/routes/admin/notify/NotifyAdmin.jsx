import { toast } from "react-toastify";
import { useState, useContext, useEffect } from "react";
import ComboList from "@/routes/components/ComboList";
import { orderStatusList as bidStatusList } from "@/routes/Index.jsx";
import styles from "../admin.module.css";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import Warning from "../Warning.jsx";

export default function NotifyAdmin() {
    const { fetchOptions, sendOptions, fetchAdminNotify, sendAdminNotify } = useContext(AdminAPIContext);
    const [opt, setOpt] = useState();
    const [subject, setSubject] = useState("");
    const [text, setText] = useState("");
    const [notify, setNotify] = useState();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);  
    const [orderStatus, setOrderStatus] = useState("new");

    useEffect(() => {
        const loadOptions = async () => {
            try {
                setLoading(true);
                const [freshOptions, freshNotify] = await Promise.all([
                    fetchOptions().then(res => res.data),
                    fetchAdminNotify().then(res => res.data)
                ]);
                setOpt(freshOptions);
                setNotify(freshNotify);
                setText(freshNotify?.[orderStatus]?.text || "");
                setSubject(freshNotify?.[orderStatus]?.subject || "");
            }
            finally {
                setLoading(false);
            }
        }
        if (!saving) loadOptions();
    }, [fetchOptions, saving]);

    useEffect(() => {
        const selectedItem = notify?.[orderStatus];
        setSubject(selectedItem?.subject || "");
        setText(selectedItem?.text || "");        
    }, [orderStatus]);


    async function save() {
        setSaving(true);
        try {
            const [optAnswer, notifyAnswer] = await Promise.all([
                sendOptions({ opt }),
                sendAdminNotify({ notify })
            ])
            if(optAnswer.type == "success" && notifyAnswer.type == "success") {
                toast.success("Уведомления сохранены");
            }
            else {
                toast.error("Не удалось сохранить уведомления");
            }
        }
        finally {
            setSaving(false);
        }
    }

    const handleChange = (e) => {
        const { name, value, type } = e.target;
        const newValue = type === "number" ? parseInt(value) : value;
        setOpt({ ...opt, [name]: newValue });
    };

    const handleChangeStatus = (key, prop, value) => {
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

    if (loading) {
        return <p>Загрузка...</p>;
    }

    if (saving) {
        return <p>Сохранение...</p>;
    }

    return(
        <>
        <h2>Уведомления администратору</h2>

        <div>
            <label>
                Токен бота:
                <input name="telegramBotToken" value={opt.telegramBotToken } onChange={handleChange} />
                <Warning message={opt?.validation?.telegramBotToken} />
            </label>
        </div>

        <div>
            <label>
                Имя бота:
                <input name="telegramBotName" value={opt.telegramBotName } onChange={handleChange} />
                <Warning message={opt?.validation?.telegramBotName} />
            </label>
        </div>

        <div>
            <label>
                ID группы в Telegram:
                <input name="telegramGroupId" value={opt.telegramGroupId } onChange={handleChange} />
            </label>
        </div>

        <label>
            Электронная почта администратора:
            <input name="adminEmail" value={opt.adminEmail } onChange={handleChange} />
        </label>

        <h3>Настроить</h3>
        
        <div className={styles.container}>
            <label>
                Статус заявки: &nbsp;&nbsp;
                <ComboList rows={bidStatusList} selectedId={orderStatus} setSelectedId={setOrderStatus} />
            </label>

            <div>
                <input placeholder="Тема письма" value={subject} onChange={e => handleChangeStatus(orderStatus, "subject", e.target.value)} />
            </div>

            <div>
                <textarea rows="8" cols="30" style={{width: "50%"}} value={text} onChange={e => handleChangeStatus(orderStatus, "text", e.target.value)} />
            </div>
{/* 
            <textarea rows="8" cols="30" style={{width: "50%", margin: "1em 0"}} value={text}
                onChange={e => handleChangeStatus(orderStatus, e.target.value)} /> */}

            Можно использовать теги в квадратных скобках, например:
            <ul style={{marginTop: "0"}}>
                <li>[id] - номер заявки;</li>
                <li>[requisites] - реквизиты (для ручных направлений);</li>
                <li>[profit] - прибыль, %;</li>
                <li>[email] - почта пользователя;</li>
                <li>[profitPercent] - прибыль, %;</li>
                <li>[profitUsdt] - прибыль, USDT;</li>
                <li>[walletFrom] - кошелек Отдаю;</li>
                <li>[walletTo] - кошелек Получаю;</li>
                <li>[amountFrom] - колич.валюты Отдаю;</li>
                <li>[amountTo] - колич валюты Получаю;</li>
                <li>[receiveType] - модуль приема: Ручной (оператор, рубли) / Авто (криптовалюта);</li>
                <li>[sendType] - модуль отправки: Ручной / Авто;</li>
                <li>[currencyFrom] - валюта Отдаю;</li>
                <li>[currencyTo] - валюта Получаю.</li>
            </ul>

            <button className={styles.save} onClick={save}>Сохранить</button>
        </div>
        </>
        
    )
}

// function EchoControl({index, setOrderStatus}) {
//     const timePeriods = ["1 мин", "5 мин", "15 мин"];

//     switch(index) {
//         case 1:
//             return(
//                 <>
//                 <ComboList rows={bidStatusList} setSelectedId={setOrderStatus} />
//                 <div>
//                     <textarea rows="8" cols="30" style={{width: "50%", margin: "1em 0"}} />
//                 </div>
//                 </>
//             )
//         case 2:
//             return(
//                 <div>
//                     <textarea rows="8" cols="30" style={{width: "50%", margin: "1em 0"}} />
//                 </div>
//             )
//         case 3:
//             return(
//                 <>
//                 <ComboList rows={timePeriods} />
//                 <input type="number" />
//                 <div className={styles.tooltipContainer}>
//                     ?
//                     <div className={styles.tooltipText}>
//                         Load Average<br />
//                         высокая загрузка = колич ядер<br />                        
//                         для 2х ядер - выше 2
//                     </div>
//                 </div>
//                 </>
//             )
//         default:
//             console.log(`Настройка уведомлений: не найдено полей с индексом {index}`);
//     }
// }