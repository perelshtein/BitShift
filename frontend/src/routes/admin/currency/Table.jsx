import { Link } from "react-router-dom";
import { ROUTES } from "@/links.js";
import { useContext } from "react";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import SelectAllToggle from "@/routes/components/SelectAllToggle.jsx";
import { toast } from "react-toastify";
import styles from "../admin.module.css";

const TableRow = ({ data, rowsSelected, setRowsSelected, activeRole, reserves, setSaving }) => {
  const isChecked = rowsSelected.includes(data.id);

  const handleCheckboxChange = () => {
    if (isChecked) {
      setRowsSelected(rowsSelected.filter(it => it !== data.id));
    } else {
      setRowsSelected([...rowsSelected, data.id]);
    }
  };
  const reserve = reserves[data.code];
  const reserveNames = {
    error: "Ошибка",
    off: "Отключен",
    manual: "Задан вручную",
    fromFile: "Из файла",
    fromExchange: "С биржи"  
  }
  const { reloadReserve } = useContext(AdminAPIContext);

  const handleFileReserve = async() => {
    try {
      setSaving(true);
      let answer = await reloadReserve({baseCurrency: data.code});
      toast.info(answer.message);
    }
    finally {
      setSaving(false);
    }
  }

  return (
    <>      
      <div className={styles.checkboxContainer}>
        <input type="checkbox" id={data.id} checked={isChecked} onChange={handleCheckboxChange} />
        <span>
          <label htmlFor={data.id}>{data.name}</label>
          <Link className={styles.links} to={`${ROUTES.EDIT_CURRENCY}/${data.id}`}>
            {activeRole.isEditCurrency ? "Редактировать.." : "Просмотреть.."}
          </Link>
        </span>
      </div>
      <div>{data.code}</div>
      <div>{data.xmlCode}</div>
      <div>{reserveNames[reserve?.reserveType] || "Отключен"}</div>
      <div className={styles.reserveColumn}>{reserve?.value} {reserve?.reserveCurrency}
        {activeRole.isEditReserve && reserve?.reserveType == "fromFile" && 
          <Link className={styles.links} onClick={handleFileReserve}>Обновить</Link>          
        }
      </div>
      <div>{data.isEnabled ? "Активна" : "Отключена"}</div>
    </>
  );
};

const TableView = ({ rowsSelected, setRowsSelected, activeRole, reserves, setSaving }) => {    
  let { currencies } = useContext(CurrencyContext);

  return (
    <div className={styles.tableSixCol}>
      <div>Название</div>
      <div>Код валюты</div>
      <div>XML-код</div>
      <div>Источник резерва</div>
      <div>Резерв</div>
      <div>Статус</div>

      {currencies.map((row, index) => (
        <TableRow key={index} data={row} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} activeRole={activeRole} 
          reserves={reserves} setSaving={setSaving} />
      ))}

      <SelectAllToggle items={currencies} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
    </div>
  );
};

export default TableView;
