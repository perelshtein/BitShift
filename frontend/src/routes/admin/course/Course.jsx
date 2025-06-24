import { useContext, useEffect, useState } from "react"
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import Table from "./Table";
import Pagination from "@/routes/components/Pagination";
import ComboList from "@/routes/components/ComboList";
import styles from "../admin.module.css";

export default function Course() {  
  const { fetchCourses } = useContext(AdminAPIContext);
  const [loading, setLoading] = useState(true);    
  const [courses, setCourses] = useState([]);
  const rowsPerPage = 100;
  const [currentPage, setCurrentPage] = useState(0);
  const [totalCoursesCount, setTotalCoursesCount] = useState(0);   
  const [filter, setFilter] = useState();
  const [filterInput, setFilterInput] = useState();
  const [filterExchange, setFilterExchange] = useState();
  const [filterExchangeInput, setFilterExchangeInput] = useState();
  
  useEffect(() => {    
    const loadCourses = async() => {
      try {
        const start = currentPage * rowsPerPage;
        const answer = (await fetchCourses({
          start: start,
          count: rowsPerPage,
          filter: filter,
          exchange: filterExchange !== "Все" ? filterExchange : undefined
        })).data;        
        setCourses(answer.items);
        setTotalCoursesCount(answer.total);
      }
      finally {
        setLoading(false);
      }
    }
    loadCourses();
  }, [currentPage, filter, filterExchange]);

  const startNewSearch = async() => {    
    setCurrentPage(0);
    setFilter(filterInput);
    setFilterExchange(filterExchangeInput);
  }

  if(loading) {
    return <p>Загрузка...</p>    
  }

  return(
  <>
    <h2>Список курсов</h2>
    <label>
      Биржа:
      <ComboList rows={["Все", "Bybit", "Binance", "CoinMarketCap", "Cbr", "Mexc"]} setSelectedId={setFilterExchangeInput} />
    </label>
    <div className={styles.horzLine}>
      <label>Фильтр: <input value={filterInput} onChange={(e) => setFilterInput(e.target.value)} placeholder="Код валюты или формула" /></label>
      <button onClick={startNewSearch}>Применить</button>
    </div>
    <Pagination
      total={totalCoursesCount}
      currentPage={currentPage}
      rowsPerPage={rowsPerPage}
      onPageChange={setCurrentPage}
      className={styles.pagination}
    />
    <Table data={courses} />
    <Pagination
      total={totalCoursesCount}
      currentPage={currentPage}
      rowsPerPage={rowsPerPage}
      onPageChange={setCurrentPage}
      className={styles.pagination}
    />
    <p>Найдено курсов: {totalCoursesCount}</p>
  </>
  )
}