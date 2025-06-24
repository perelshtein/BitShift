import { useContext, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import ComboList from "@/routes/components/ComboList";
import Pagination from "@/routes/components/Pagination";
import Table from "./TableFormula";
import { ROUTES } from "@/links";
import { toast } from 'react-toastify';
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import styles from "../admin.module.css";

export default function Formula() {
    const { fetchFormulas, deleteFormulas, sendFormula } = useContext(AdminAPIContext);
    const { activeRole, loading: roleLoading } = useContext(UsersRolesContext);
    const navigate = useNavigate();
    const rowsPerPage = 100;
    const [currentPage, setCurrentPage] = useState(0);
    const [formulas, setFormulas] = useState([]);
    const [totalFormulasCount, setTotalFormulasCount] = useState(0);
    const [rowsSelected, setRowsSelected] = useState([]);
    const [isNeedUpdate, setIsNeedUpdate] = useState(false);
    const [filterStatus, setFilterStatus] = useState();
    const [filterText, setFilterText] = useState();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        const loadFormulas = async () => {
            setLoading(true);
            try {
                const start = currentPage * rowsPerPage;
                const answer = (await fetchFormulas({
                    start: start,
                    count: rowsPerPage,
                    status: filterStatus == "all" ? undefined : filterStatus,
                    filter: filterText == "" ? undefined : filterText
                })).data;
                setFormulas(answer.items);
                setTotalFormulasCount(answer.total);
            }
            finally {
                setLoading(false);
            }
        }
        if (isNeedUpdate) {
            loadFormulas();
            setIsNeedUpdate(false);
        }
        loadFormulas();
    }, [currentPage, isNeedUpdate]);

    const status = [
        { id: "all", name: "Все" },
        { id: "active", name: "Активные" },
        { id: "inactive", name: "Отключенные" }
    ];

    const startNewSearch = () => {
        setCurrentPage(0);
        setIsNeedUpdate(true);
    }

    const handleDelete = async () => {
        try {
            setSaving(true);
            const answer = await deleteFormulas({ ids: rowsSelected });
            setFormulas([...formulas.filter(it => !rowsSelected.includes(it.id))]);
            setRowsSelected([]);
            toast.info(answer.message);
        }
        finally {
            setSaving(false);
        }
    }

    const handleOnOff = async (isOn) => {
        try {
            setSaving(true);
            //параллельно сохраняем формулы
            let updatedFormulas = [...formulas];
            const formulaPromises = rowsSelected.map(async rowId => {
                const formula = formulas.find(e => e.id == rowId);
                if (formula) {
                    const newFormula = { ...formula, isEnabled: isOn };
                    await sendFormula({ formula: newFormula });

                    updatedFormulas = updatedFormulas.map(c =>
                        c.id === rowId ? newFormula : c
                    );

                    return `${formula.from} -> ${formula.to}`;
                }
                return null;
            });

            const formulaNames = (await Promise.all(formulaPromises)).filter(Boolean); // пропустим null-значения
            setRowsSelected([]);
            setFormulas(updatedFormulas);
            const state = isOn ? "включены" : "отключены";
            toast(`Формулы ${state}: ${formulaNames}`);
        }
        finally {
            setSaving(false);
        }
    }

    const SelectedActions = () => {
        return (
            <div className={styles.actionButtons}>
                <p>Выбрано {rowsSelected.length} элементов.</p>
                <button onClick={() => handleOnOff(true)}>Активировать</button>
                <button onClick={() => handleOnOff(false)}>Деактивировать</button>
                <button onClick={handleDelete}>Удалить</button>
            </div>
        );
    };

    if (loading || roleLoading) {
        return <p>Загрузка...</p>
    }

    if (saving) {
        return <p>Сохранение...</p>
    }

    return (
        <>
            <h2>Формулы</h2>
            {activeRole.isEditCurrency && <button className={styles.addButton} onClick={() => { navigate(ROUTES.EDIT_FORMULA); }}>Добавить..</button>}
            <div className={styles.horzList}>Статус: <ComboList rows={status} setSelectedId={setFilterStatus} /></div>
            <div className={styles.horzList}>
                <label>Фильтр: <input value={filterText} placeholder="Код валюты или формула" onChange={e => setFilterText(e.target.value)} /></label>
                <button onClick={startNewSearch}>Применить</button>
            </div>

            <Pagination
                total={totalFormulasCount}
                currentPage={currentPage}
                rowsPerPage={rowsPerPage}
                onPageChange={setCurrentPage}
                className={styles.pagination}
            />

            <Table data={formulas} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
            {rowsSelected.length > 0 && <SelectedActions rowsSelected={rowsSelected} />}

            <Pagination
                total={totalFormulasCount}
                currentPage={currentPage}
                rowsPerPage={rowsPerPage}
                onPageChange={setCurrentPage}
                className={styles.pagination}
            />
            <p>Найдено формул: {totalFormulasCount}</p>
        </>
    );
}