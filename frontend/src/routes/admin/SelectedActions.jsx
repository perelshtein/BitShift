import styles from "./admin.module.css";

const SelectedActions = ({rowsSelected}) => {
    return (
      <div className={styles.actionButtons}>      
        <p>Выбрано {rowsSelected.length} элементов.</p>
        <button>Активировать</button>
        <button>Деактивировать</button>
        <button>Удалить</button>
      </div>
    );
  };

  export default SelectedActions