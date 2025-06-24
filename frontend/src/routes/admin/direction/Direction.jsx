import { useContext, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { CommonAPIContext } from "@/context/CommonAPIContext.jsx";
import Table from "./TableDirection";
import SelectedActions from "./SelectedActions";
import { ROUTES } from "@/links";
import Pagination from "@/routes/components/Pagination";
import ComboList from "@/routes/components/ComboList.jsx";
import styles from "../admin.module.css";

export default function Direction() {
    const { currencies } = useContext(CurrencyContext);
    const { fetchDirections } = useContext(CommonAPIContext);
    const { activeRole, loading: rolesLoading } = useContext(UsersRolesContext); 
    const [directions, setDirections] = useState([]);
    const [rowsSelected, setRowsSelected ] = useState([]);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const navigate = useNavigate();

    const rowsPerPage = 100;
    const [currentPage, setCurrentPage] = useState(0);
    const [totalDirectionsCount, setTotalDirectionsCount] = useState(0);
    const [isNeedUpdate, setIsNeedUpdate] = useState(false);
    const [filterStatus, setFilterStatus] = useState();
    const [filterGive, setFilterGive] = useState();
    const [filterGet, setFilterGet] = useState();
    const [filterText, setFilterText] = useState();
    const modRows = [
      {id: "all", name: "Все валюты"},
      ...currencies.map(it => ({id: it.id, name: it.name}))
    ];
    const status = [
      {id: "all", name: "Все"},
      {id: "active", name: "Активные"},
      {id: "inactive", name: "Неактивные"}
    ];

    useEffect(() => {
      const loadDirections = async() => {
        setLoading(true);
        try {
          const start = currentPage * rowsPerPage;
          const directionsData = (await fetchDirections({
            start: start,
            count: rowsPerPage,             
            filter: filterText == "" ? undefined : filterText,
            status: filterStatus == "all" ? undefined : filterStatus,
            fromId: filterGive == "all" ? undefined : filterGive,
            toId: filterGet == "all" ? undefined : filterGet
          })).data;
          setTotalDirectionsCount(directionsData.total);
          setDirections(directionsData.items);          
        } finally {
          setLoading(false);
        }
      }
      loadDirections();
    }, [currentPage, isNeedUpdate]);

    const startNewSearch = async() => {
      setCurrentPage(0);
      setIsNeedUpdate(!isNeedUpdate);
    }

    if(loading || rolesLoading) {
      return <p>Загрузка...</p>    
    }

    return (
      <>
        <h2>Направления обменов</h2>
        {activeRole.isEditDirection && <button className={styles.addButton} onClick={() => { navigate(ROUTES.EDIT_DIRECTION); }}>Добавить..</button>}
        
        <div className={styles.horzList}>
          Отдаю: <ComboList rows={ modRows } selectedId={filterGive} setSelectedId={setFilterGive} /> 
          Получаю: <ComboList rows={ modRows } selectedId={filterGet} setSelectedId={setFilterGet} />
        </div>
        <div className={styles.horzList}>Статус: <ComboList rows={ status } selectedId={filterStatus} setSelectedId={setFilterStatus} /></div>
        <div className={styles.horzList}>
          <label>Фильтр: <input value={filterText} placeholder="Код валюты" onChange={e => setFilterText(e.target.value)} /></label>
          <button onClick={startNewSearch}>Применить</button>
        </div>
        <Pagination
              total={totalDirectionsCount}
              currentPage={currentPage}
              rowsPerPage={rowsPerPage}
              onPageChange={setCurrentPage}
              className={styles.pagination}
        />
        
        { rowsSelected.length > 0 && <SelectedActions rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} 
          directions={directions} setDirections={setDirections} setSaving={setSaving} /> }

        <Table data={directions} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />    

        <Pagination
              total={totalDirectionsCount}
              currentPage={currentPage}
              rowsPerPage={rowsPerPage}
              onPageChange={setCurrentPage}
              className={styles.pagination}
        />    
        <p>Найдено направлений: {totalDirectionsCount}</p>
      </>
    );
  }