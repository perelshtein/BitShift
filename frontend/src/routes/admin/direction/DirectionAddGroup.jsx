import { useContext } from "react";
import CheckboxList from "@/routes/components/CheckboxList";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import styles from "../admin.module.css";

export default function DirectionAddGroup() {
    const { currencyList } = useContext(CurrencyContext);

    return (
        <>
            <h2>Добавить группу направлений</h2>
            <h3>Отдаю - получаю</h3>
            <div className={styles.tableTwoColLayout} style={{gridTemplateColumns: "1fr 1fr"}}>
                <CheckboxList rows={currencyList} className={styles.checkboxList}/>
                <CheckboxList rows={currencyList} className={styles.checkboxList}/>                
            </div>

            <div className={styles.actionButtons}>
                <button className={styles.save}>Сохранить</button>
            </div>
        </>
    )
}