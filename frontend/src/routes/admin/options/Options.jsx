import { toast } from "react-toastify";
import { useContext, useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { ROUTES } from "@/links";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import ComboList from "@/routes/components/ComboList.jsx";
import Clock from "../Time.jsx";
import TableView from "./Table.jsx";
import { timeZones, idToMinutes, minutesToId, minutesToText } from "./TimeZones.jsx";
import styles from "../admin.module.css";
import Warning from "../Warning.jsx";

export default function Options() {
    const { fetchOptions, sendOptions } = useContext(AdminAPIContext);
    const [opt, setOpt] = useState();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [selectedTimezone, setSelectedTimezone] = useState();

    const currencies = useContext(CurrencyContext).currencies
        .map(it => ({ ...it, name: it.code }))
        .reduce((accumulator, current) => {
            const isCodeExists = accumulator.some(item => item.code === current.code);
            if (!isCodeExists) {
                accumulator.push(current);
            }
            return accumulator;
        }, []);

    const navigate = useNavigate();
    const [rowsSelected, setRowsSelected] = useState([]);

    // const handleNumberChange = (e) => {
    //     const newOpt = { ...opt, [e.target.name]: parseInt(e.target.value) };
    //     setOpt(newOpt);
    // }

    const handleChange = (e) => {
        const { name, value, type } = e.target;
        const newValue = type === "number" ? parseInt(value) : type === "checkbox" ? e.target.checked : value;
        setOpt({ ...opt, [name]: newValue });
    };
    

    useEffect(() => {
        const loadOptions = async () => {
            try {
                setLoading(true);
                const answer = await fetchOptions();
                setOpt(answer.data);
            }
            finally {
                setLoading(false);
            }
        }
        if (!saving) loadOptions();
    }, [fetchOptions, saving]);

    useEffect(() => {
        if (opt?.serverTimezoneMinutes !== undefined) {
            let timezoneId = minutesToId(opt.serverTimezoneMinutes);
            setSelectedTimezone(timezoneId);
        }
    }, [opt?.serverTimezoneMinutes]);

    if (loading) {
        return <p>Загрузка...</p>;
    }

    if (saving) {
        return <p>Сохранение настроек...</p>;
    }

    if (!opt) {
        return <p>Ошибка загрузки страницы.</p>;
    }

    // const browserTimezoneOffset = new Date().getTimezoneOffset();

    const timetableRows = [
        { id: 1, time: "Пн-Пт 10:00-22:00", currency: "RUB-*" },
        { id: 2, time: "Сб-Вс 13:00-22:00", currency: "RUB-*" }
    ]

    return (
        <>
            <h2>Настройки</h2>
            <label className={styles.oneLine}>
                <input type="checkbox" name="isExportCourses" checked={opt.isExportCourses} onChange={handleChange} />
                Экспорт курсов
            </label>

            <label className={styles.oneLine}>
                <input type="checkbox" name="isMaintenance" checked={opt.isMaintenance} onChange={handleChange} />
                Режим тех.обслуживания
            </label>

            <h3>Защита от атак</h3>
            <label className={styles.oneLine}>
                <input type="checkbox" name="isRandomCookie" checked={opt.isRandomCookie} onChange={handleChange} />
                Включить random-cookie
            </label>
            <label className={styles.withMargin}>
                Интервал, минут:
                <input type="number" name="randomCookieInterval" value={opt.randomCookieInterval || ""} onChange={handleChange} />
            </label>
            <label className={styles.withMargin}>
                Сколько раз разрешено открыть сайт без кук:
                <input type="number" name="randomCookieAttempts" value={opt.randomCookieAttempts || ""} onChange={handleChange} />
            </label>

            <label>
                Таймаут сессии (минут):
                <input type="number" name="sessionTimeoutMinutes" value={opt.sessionTimeoutMinutes || ""} onChange={handleChange} />
            </label>

            <h4>Максимальное количество запросов в минуту, на каждый IP:</h4>
            <label className={styles.withMargin}>
                Вход на сайт, создание заявок:
                <input type="number" name="maxRequestsSerious" value={opt.maxRequestsSerious || ""} onChange={handleChange} />
                <Warning message={opt?.validation?.maxRequestsSerious} />
            </label>

            <label className={styles.withMargin}>
                Остальные маршруты:
                <input type="number" name="maxRequests" value={opt.maxRequests || ""} onChange={handleChange} />
                <Warning message={opt?.validation?.maxRequests} />
            </label>

            <h3 style={{ marginBottom: "0" }}>Почта</h3>            
            <label className={styles.withMargin}>
                Сервер:
                <input name="smtpServer" value={opt.smtpServer || ""} onChange={handleChange} />
                <Warning message={opt?.validation?.smtpServer} />
            </label>

            <label className={styles.withMargin}>
                Порт:
                <input type="number" name="smtpPort" value={opt.smtpPort || ""} onChange={handleChange} />
                <Warning message={opt?.validation?.smtpPort} />
            </label>

            <label className={styles.withMargin}>
                Логин:
                <input name="smtpLogin" value={opt.smtpLogin || ""} onChange={handleChange} />
                <Warning message={opt?.validation?.smtpLogin} />
            </label>

            <label className={styles.withMargin}>
                Пароль:
                <input name="smtpPassword" value={opt.smtpPassword || ""} onChange={handleChange} />
                <Warning message={opt?.validation?.smtpPassword} />
            </label>

            <h3 style={{ marginBottom: "0" }}>Часовой пояс</h3>
            <ComboList rows={timeZones} setSelectedId={setSelectedTimezone} selectedId={selectedTimezone} />
            <Warning message={opt?.validation?.serverTimezoneMinutes} />
            <label className={styles.oneLine}>
                Текущее время:&nbsp;
                <Clock serverTimezoneOffset={opt.serverTimezoneMinutes} />
                <br />
            </label>

            <h3>Расписание валют</h3>
            <button style={{ margin: "0 0 0.5em 0" }} onClick={() => { navigate(ROUTES.EDIT_TIMETABLE); }}>Добавить..</button>
            <TableView data={timetableRows} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
            {rowsSelected.length > 0 && <SelectedActions rowsSelected={rowsSelected} />}

            <button id="save" onClick={save} >Сохранить</button>

        </>
    )

    function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async function save() {
        // это делается, чтобы сразу получить объект с актуальными свойствами
        // на setState() полагаться нельзя, он асинхронный
        const updatedOpt = { ...opt, serverTimezoneMinutes: parseInt(idToMinutes(selectedTimezone)) }

        setOpt(updatedOpt);
        setSaving(true);        
        try {
            let answer = await sendOptions({ opt: updatedOpt });
            toast.info(answer.message);
        }
        finally {
            setSaving(false);            
        }
    }
}

const SelectedActions = ({ rowsSelected }) => {
    return (
        <div className={styles.actionButtons}>
            <p>Выбрано {rowsSelected.length} элементов.</p>
            <button>Удалить</button>
        </div>
    );
};