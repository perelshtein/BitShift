import { useLocation, useParams } from 'react-router-dom';
import styles from "../admin.module.css";
import { orderStatusList, orderSrcList } from '@/routes/Index';

export default function OrderLog() {
    const { id } = useParams();
    const location = useLocation();
    const { statusHistory } = location.state || {}; //загрузим данные с пред страницы

    if (!statusHistory || statusHistory.length == 0) {
        return (
            <>
            <h2>Лог заявки {id}</h2>
            <div>Лог заявки пуст.</div>
            </>
        )
    }

    return(
        <>
        <h2>Лог заявки {id}</h2>
        <div className={styles.tableThreeCol}>
            <div>Дата</div>
            <div>Где сделано</div>
            <div>Cтатус</div>

            {statusHistory.map((entry) => (
                <>
                <div>{new Date(entry.date).toLocaleString()}</div>
                <div>{orderSrcList.find(it => it.id === entry.src).name || entry.src}</div>
                <div>{orderStatusList.find(it => it.id === entry.status).name || entry.status}</div>
                </>
            ))}
        </div>
        </>
    )
}