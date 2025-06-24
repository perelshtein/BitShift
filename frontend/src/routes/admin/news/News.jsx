import { Link, useNavigate } from "react-router-dom";
import { toast } from 'react-toastify';
import { useState, useEffect, useContext } from "react";
import { ROUTES } from "@/links.js";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { CommonAPIContext } from "@/context/CommonAPIContext.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import Pagination from "@/routes/components/Pagination.jsx";
import SelectAllToggle from "@/routes/components/SelectAllToggle.jsx";
import styles from "../admin.module.css";

export default function News() {
    const [newsList, setNewsList] = useState([]);
    const [rowsSelected, setRowsSelected ] = useState([]);   
    const [loading, setLoading] = useState(true);
    const [deleting, setDeleting] = useState(false);    
    
    const [currentPage, setCurrentPage] = useState(0);
    const [totalNewsCount, setTotalNewsCount] = useState(0);
    const [updateUsers, setUpdateUsers] = useState(false);
    const rowsPerPage = 10;

    const navigate = useNavigate(); 
    const { fetchNews } = useContext(CommonAPIContext);
    const { deleteNews } = useContext(AdminAPIContext);
    const { activeRole, loading: roleLoading } = useContext(UsersRolesContext);    

    useEffect(() => {
      const loadNews = async() => {
        try {
          const start = currentPage * rowsPerPage;
          const answer = (await fetchNews({start: start, count: rowsPerPage})).data;
          setTotalNewsCount(answer.total);
          setNewsList(answer.items);          
        }
        finally {
          setLoading(false);
        }
      }
      loadNews();
    }, [currentPage, updateUsers]);

    if(loading || roleLoading || !activeRole) {
      return <p>Загрузка...</p>
    }

    if(deleting) {
      return <p>Удаление...</p>
    }

    const handleDelete = async() => {
      try {
        setDeleting(true);
        const answer = await deleteNews({ids: rowsSelected});
        setRowsSelected([]);
        setUpdateUsers(updateUsers ? false : true);
        toast(answer.message);
      }
      finally {        
        setDeleting(false);
      }
    }

    const TableRow = ({ data: row }) => {
      return (
        <>
          <div className={styles.checkboxContainer}>
            <input id={row.id} type="checkbox" checked={rowsSelected.includes(row.id)} disabled={!activeRole.isEditNews}
              onChange={() => {
                if (rowsSelected.includes(row.id)) {
                  setRowsSelected(rowsSelected.filter(it => it !== row.id));
                } else {
                  setRowsSelected([...rowsSelected, row.id]);
                }
              }} />
              <span>
                <label htmlFor={row.id}>
                  {new Date(row.date).toLocaleString()}
                </label>
                <p className={styles.links}>
                  <Link to={`${ROUTES.EDIT_NEWS}/${row.id}`}>{activeRole.isEditNews ? "Редактировать.." : "Просмотреть..."}</Link>
                </p>
              </span>
          </div>  
          <div>{row.caption}</div>
        </>
      );
    };
    
    const TableNews = ({ data }) => {
      return (
        <div className={styles.tableTwoCol}>
          <div>Дата</div>
          <div>Заголовок</div>
            
          {data.map((row) => (
            <TableRow key={row.id} data={row} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
          ))}
          <SelectAllToggle items={data} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
        </div>
      );
    };
  
    const SelectedActions = () => {
      return (
        <div className={styles.actionButtons}>      
          <p>Выбрано {rowsSelected.length} элементов.</p>
          <button onClick={handleDelete}>Удалить</button>
        </div>
      );
    };

    return(
        <>
        <h2>Новости</h2>
        {activeRole.isEditNews && <button className={styles.addButton} onClick={() => { navigate(ROUTES.EDIT_NEWS); }}>Добавить..</button>}
        <TableNews data={newsList} />
        <Pagination
          total={totalNewsCount}
          currentPage={currentPage}
          rowsPerPage={rowsPerPage}
          onPageChange={setCurrentPage}
          className={styles.pagination}
        />
        { rowsSelected.length > 0 && <SelectedActions /> }
        </>
    )
}