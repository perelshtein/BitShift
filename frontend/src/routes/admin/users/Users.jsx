import { useContext, useState, useEffect } from "react";
import { toast } from 'react-toastify';
import { useNavigate } from "react-router-dom";
import ComboList from "@/routes/components/ComboList";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import Table from "./TableUsers";
import { ROUTES } from "@/links";
import Pagination from "@/routes/components/Pagination.jsx";
import styles from "../admin.module.css";


export default function Users() {
  const { loading: rolesLoading, roles, activeRole } = useContext(UsersRolesContext);
  const { fetchUsers, deleteUsers, fetchOptions } = useContext(AdminAPIContext);
  const navigate = useNavigate();
  
  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState([]);
  const [selectedUsers, setSelectedUsers] = useState([]);

  const [currentPage, setCurrentPage] = useState(0);
  const [totalUsersCount, setTotalUsersCount] = useState(0);
  const rowsPerPage = 10;
  const [query, setQuery] = useState("");

  const [deleting, setDeleting] = useState(false);
  const [selectedRoleId, setSelectedRoleId] = useState();
  const [resetSearch, setResetSearch] = useState(true);
  const [options, setOptions] = useState();

  const loadUsers = async() => {
    try {
      setLoading(true);
      const start = currentPage * rowsPerPage;
      const [answerUsers, answerOptions] = await Promise.all([
          fetchUsers({
          start: start,
          count: rowsPerPage,
          query: query,
          roleId: selectedRoleId
        }).then(res => res.data),
          fetchOptions().then(res => res.data)
      ]);      
      setUsers(answerUsers.items);
      setTotalUsersCount(answerUsers.total);
      setOptions(answerOptions);  
    }
    finally {
      setLoading(false);
    }
  }

  const resetFilter = async() => {
    setQuery("");    
    setSelectedRoleId(null);
    currentPage === 0 ? setResetSearch(resetSearch ? false : true) : setCurrentPage(0);
  }

  const applyFilter = async() => {    
    currentPage === 0 ? loadUsers() : setCurrentPage(0);
  }

  useEffect(() => {    
    loadUsers();
  }, [currentPage, resetSearch]);

  const handleDelete = async () => {
    try {
      setDeleting(true);
      const result = await deleteUsers({ids: selectedUsers});
      setSelectedUsers([]);
      toast.info(result.message);
      loadUsers();
    } finally {
      setDeleting(false);
    }
  };

  const SelectedActions = ({ rowsSelected }) => (
    <div className={styles.actionButtons}>
      <p>Выбрано {rowsSelected.length} элементов.</p>
      <button onClick={handleDelete}>Удалить</button>
    </div>
  );

  if (loading || rolesLoading) {
    return <p>Загрузка...</p>;
  }

  if (deleting) {
    return <p>Удаление...</p>;
  }

  return (
    <>
      <h2>Пользователи</h2>
      {activeRole.isEditUserAndRole &&
        <button
          className={styles.addButton}
          onClick={() => {            
            navigate(ROUTES.EDIT_USER);
          }}
        >
          Добавить..
        </button>
      }
      <div className={styles.tableTwoColLayout}>
        <label htmlFor="filter">Фильтр:</label>
        <input
          id="filter"
          placeholder="Имя или почта"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />

        <label htmlFor="role">Роль:</label>
        <ComboList
          id="role"
          rows={[{ id: null, name: "Все роли" }, ...roles]}
          selectedId={selectedRoleId}
          setSelectedId={setSelectedRoleId}
        />
        <button onClick={resetFilter}>Сброс</button>
        <button onClick={applyFilter}>Применить фильтр</button>
      </div>

      <Table
        data={users}
        rowsSelected={selectedUsers}
        setRowsSelected={setSelectedUsers}
        defaultCashback={options.cashbackPercent}
      />      
      {selectedUsers.length > 0 && (
        <SelectedActions rowsSelected={selectedUsers} />
      )}
      <Pagination
          total={totalUsersCount}
          currentPage={currentPage}
          rowsPerPage={rowsPerPage}
          onPageChange={setCurrentPage}
          className={styles.pagination}
        />      
    </>
  );
}
