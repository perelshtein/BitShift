
import { useState, useEffect, useContext } from "react";
import styles from "../admin.module.css";
import TableReviews from "./Table";
import { AdminAPIContext } from "@/context/AdminAPIContext";
import Nested from "@/routes/components/Nested/Nested";
import Pagination from "@/routes/components/Pagination";
import { toast } from "react-toastify";
import ComboList from "./ComboList";
import { reviewStatusList, omit } from "@/routes/Index";

export default function Reviews() {
    const [rowsSelected, setRowsSelected ] = useState([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [reviews, setReviews] = useState();
    const { fetchReviews, sendReview } = useContext(AdminAPIContext);
    const [ totalReviewsCount, setTotalReviewsCount ] = useState();
    const [currentPage, setCurrentPage] = useState(0);
    const [isVisible, setIsVisible] = useState(false);
    const rowsPerPage = 10;    
    const [queryOptions, setQueryOptions] = useState({});

    const handleChange = (e) => {
      // Check if e is a synthetic event or custom object
      const name = e?.target?.name || e.name;
      const value = e?.target?.value || e.value;

      setQueryOptions((prev) => ({
          ...prev,
          [name]: value,
      }));
  };

    const loadReviews = async() => {
      try {
          setLoading(true);  
          const start = currentPage * rowsPerPage;
          let filteredQueryOptions = queryOptions.status === "Все"
              ? omit(queryOptions, "status")
              : queryOptions;
          filteredQueryOptions = filteredQueryOptions.rating == "Все"
            ? omit(filteredQueryOptions, "rating")
            : filteredQueryOptions;                     
          let answer = await fetchReviews({...filteredQueryOptions, start: start, count: rowsPerPage, textSize: 15,
            dateStart: queryOptions.dateStart?.concat("T00:00"), dateEnd: queryOptions.dateEnd?.concat("T00:00")
          })
          setTotalReviewsCount(answer.data.total);
          setReviews(answer.data.items);
      }        
      finally {
          setLoading(false);
      }
    }

    const handleState = async (state) => {
      try {
        setSaving(true);        
        await Promise.all(
          rowsSelected.map((it) =>
            sendReview({ review: { id: it, status: state } })
          )
        );
        toast.info("Статус обновлен");
      } finally {
        setRowsSelected([]);
        setSaving(false);
        loadReviews();
      }
    };

    const SelectedActions = ({rowsSelected}) => {
      return (
        <div className={styles.actionButtons}>      
          <p>Выбрано {rowsSelected.length} отзывов.</p>
          <button onClick={() => handleState("approved")}>Опубликовать</button>
          <button onClick={() => handleState("moderation")}>На модерацию</button>
          <button onClick={() => handleState("banned")}>Забанить</button>
          <button onClick={() => handleState("deleted")}>Удалить</button>
        </div>
      );
    };

    const handleReset = () => {
      //console.log(queryOptions);
      currentPage == 0 ? loadReviews() : setCurrentPage(0);
    }

    useEffect(() => {
      loadReviews();        
    }, [currentPage]);
    
    if (loading) {
        return <p>Загрузка...</p>
    }

    if (saving) {
        return <p>Сохранение...</p>
    }

    return(
        <>
        <h2>Список отзывов</h2>        
        <Nested title="Фильтр" isVisible={isVisible} setIsVisible={setIsVisible} styles={styles}
          child={
              <div className={styles.tableOrders}>
                  <div>
                      <label>
                          Дата от: <input type="date" value={queryOptions?.dateStart} name="dateStart" onChange={handleChange} />
                      </label>
                      <label>
                          Дата до: <input type="date" value={queryOptions?.dateEnd} name="dateEnd" onChange={handleChange} />
                      </label>
                  </div>

                  <label>Имя пользователя:
                      <input name="userName" value={queryOptions?.userName} onChange={handleChange} />
                  </label>

                  <label>Почта:
                      <input name="userMail" value={queryOptions?.userMail} onChange={handleChange} />
                  </label>

                  <label>Статус:
                      <ComboList name="status" rows={["Все", ...reviewStatusList]} selectedId={queryOptions?.status} setSelectedId={handleChange} />
                  </label>

                  <label>Оценка:
                      <ComboList name="rating" rows={["Все", "1", "2", "3", "4", "5"]} selectedId={queryOptions?.rating} setSelectedId={handleChange} />
                  </label>

                  <label>Текст:
                      <input name="text" value={queryOptions?.text} onChange={handleChange} />                            
                  </label>
                  
                  <button onClick={handleReset}>Применить фильтр</button>
          </div>} />

        {reviews.length > 0 ? (
          <>            
            <TableReviews
              data={reviews}
              rowsSelected={rowsSelected}
              setRowsSelected={setRowsSelected}
            />
            <Pagination
              total={totalReviewsCount}
              currentPage={currentPage}
              rowsPerPage={rowsPerPage}
              onPageChange={setCurrentPage}
              className={styles.pagination}
            />
          </>
        ) : (
          "Нет отзывов"
        )}
        { rowsSelected.length > 0 && <SelectedActions rowsSelected={rowsSelected} /> }        
        </>
    )
}