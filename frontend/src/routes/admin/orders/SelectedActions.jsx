import { useEffect, useState, useContext } from "react";
import { AdminAPIContext } from "@/context/AdminAPIContext";
import ComboList from "@/routes/components/ComboList";
import styles from "../admin.module.css";
import { toast } from "react-toastify";

const SelectedActions = ({rowsSelected, setRowsSelected, status, setSaving, loadOrders}) => {
  const { sendOrders } = useContext(AdminAPIContext);
  const [selectedStatus, setSelectedStatus] = useState(status[0].id);

  const handleSave = async() => {
    try {
      setSaving(true);
      let answer = await sendOrders({ids: rowsSelected, status: selectedStatus});      
      toast.info(answer.message);
      setRowsSelected([]);
      loadOrders();
    }
    finally {
      setSaving(false);
    }
  }


    return (
      <div className={[styles.actionButtons, styles.selectedOrders].join(' ')}>      
        <p>Выбрано {rowsSelected.length} заявок.</p>
        <label>
          Статус заявки:
          <ComboList rows={status} selectedId={selectedStatus} setSelectedId={setSelectedStatus} />          
        </label>
        <button onClick={handleSave}>Сохранить</button>
      </div>
    );
  };

  export default SelectedActions