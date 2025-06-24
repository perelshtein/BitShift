import React from 'react';
import { Link } from "react-router-dom";
import { ROUTES } from "@/links";
import styles from "@/routes/public/public.module.css";
import { reviewStatusList } from "@/routes/Index";
import StarRating from '@/routes/components/StarRating/StarRating';

const TableRow = ({ data, rowsSelected, setRowsSelected }) => {
    return (
        <>
            <div>   
                <label>
                <input type="checkbox" 
                    id={data.id} checked={rowsSelected.includes(data.id)}
                    onChange={() => {
                        if (rowsSelected.includes(data.id)) {
                            setRowsSelected(rowsSelected.filter(it => it !== data.id));
                        } else {
                            setRowsSelected([...rowsSelected, data.id]);
                        }
                }} />
                {new Date(data.date).toLocaleString()}
                </label>
                <div style={{display: "grid", placeItems: "center"}}>
                    <StarRating id="rating" value={data?.rating} size={20} disabled />
                </div>
            </div>
            <div>
                {data.caption}                
            </div>
            <div>
                <a href={`${ROUTES.MY_REVIEW_BY_ID}/${data.id}`}>
                    {data.text}
                </a>
            </div>
            <div>{reviewStatusList.find(it => it.id == data.status)?.name || data.status}</div>
        </>
    );
};

const TableView = ({ data, rowsSelected, setRowsSelected }) => {
    if(data.length == 0) return (        
        <p>Нет отзывов</p>        
    )
    return (
        <div className={styles.tableFourCol}>
            <div>Дата | Оценка</div>
            <div>Имя</div>
            <div>Текст</div>
            <div>Статус</div>

            {data.map((row, index) => (
                <TableRow key={index} data={row} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
            ))}
        </div>
    );
};

export default TableView;
