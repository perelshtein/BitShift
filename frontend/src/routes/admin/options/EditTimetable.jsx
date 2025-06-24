import { useParams } from "react-router-dom";
import { useContext, useState } from "react";
import ComboList from "@/routes/components/ComboList";
import CheckboxList from "@/routes/components/CheckboxList";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import styles from "../admin.module.css";

export default function EditTimetable() {
    const { id } = useParams();
    const currencyTypesList=[
        {id: 1, name: "Код валюты"},
        {id: 2, name: "Название валюты"}
    ];
    const [currencyTypeGive, setCurrencyTypeGive] = useState(1);
    const [currencyTypeGet, setCurrencyTypeGet] = useState(1);
    const { currencies, loading } = useContext(CurrencyContext);
    const currencyNamesList = [...currencies.map(e => (e.name)), "Все валюты"];   
    const daysOfWeek = getDaysOfWeek("ru-RU");

    const codesList = [...currencies
        .map(e => (e.code))
        .reduce((accumulator, current) => {
            const isCodeExists = accumulator.some(item => item === current);
            if (!isCodeExists) {
              accumulator.push(current);
            }
            return accumulator;
          }, []), "Все валюты"];  
          
    if(loading) {
        return <h2>Загрузка...</h2>
    }


    return(
        <>
        <Caption id={id} />
        <div className={[styles.tableTwoColLayout, styles.editTimetable].join(" ")}>            
            <label>
                <b>Отдаю:</b>
                <ComboList rows={currencyTypesList} setRows={setCurrencyTypeGive} />
                <CheckboxList rows={currencyTypeGive == 1 ? codesList : currencyNamesList} className={styles.checkboxList} />
            </label> 
            <label>
                <b>Получаю:</b>
                <ComboList rows={currencyTypesList} setRows={setCurrencyTypeGet} />
                <CheckboxList rows={currencyTypeGet == 1 ? codesList : currencyNamesList} className={styles.checkboxList} />
            </label>
            <div>
                <label>
                    <b style={{marginRight: "0"}}>С:</b> <input type="time" />
                </label>
                <label>
                    <b style={{marginRight: "0"}}>до:</b> <input type="time" />
                </label>
                <CheckboxList rows={daysOfWeek} className={styles.checkboxList} />
            </div>

        </div>
        <button className={styles.save}>Сохранить</button>
        </>
    )
}

function Caption({ id }) {
    return id ? <h2>Редактировать расписание - { id }</h2> : <h2>Добавить расписание</h2>;
}

const getDaysOfWeek = (locale) => {
    const formatter = new Intl.DateTimeFormat(locale, { weekday: 'long' });
    const days = [];
    const date = new Date();
    
    // Ищем ближайший понедельник
    const day = date.getDay(); // 0 = вс, 1 = пн, ..., 6 = сб
    const diff = (day === 0 ? -6 : 1) - day;
    date.setDate(date.getDate() + diff);

    for (let i = 0; i < 7; i++) {
        days.push(formatter.format(date));
        date.setDate(date.getDate() + 1);
    }
    return days;
};