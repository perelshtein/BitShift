import { useState, useContext, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from 'react-toastify';
import TableView from "./Table";
import { ROUTES } from "@/links";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { CommonAPIContext } from "@/context/CommonAPIContext.jsx";
import styles from "../admin.module.css";

export default function Currency() {
    const [rowsSelected, setRowsSelected ] = useState([]);
    const [saving, setSaving] = useState(false);
    const [loading, setLoading] = useState(true);    
    const [reserves, setReserves] = useState([]);
    const navigate = useNavigate();    
    const { activeRole, loading: rolesLoading } = useContext(UsersRolesContext);
    const { deleteCurrencies, sendCurrency } = useContext(AdminAPIContext);
    const { currencies, setCurrencies } = useContext(CurrencyContext);    
    const { fetchReserves } = useContext(CommonAPIContext);    

    const handleOnOff = async(isOn) => {
      try {
        setSaving(true); 
        //параллельно сохраняем валюты
        let updatedCurrencies = [...currencies];
        const currencyPromises = rowsSelected.map(async rowId => {
          const currency = currencies.find(e => e.id == rowId);
          if (currency) {
              const cur = { ...currency, isEnabled: isOn };
              await sendCurrency({currency: cur});              
              
              updatedCurrencies = updatedCurrencies.map(c => 
                c.id === rowId ? cur : c
              );
              
              return currency.name;
          }
          return null;
        });

        const currencyNames = (await Promise.all(currencyPromises)).filter(Boolean); // пропустим null-значения        
        setRowsSelected([]);
        setCurrencies(updatedCurrencies);
        const state = isOn ? "включены" : "отключены";
        toast(`Валюты ${state}: ${currencyNames}`);
      }
      finally {
        setSaving(false);
      }
    }

    const handleDelete = async() => {
      try {
        setSaving(true);
        const answer = await deleteCurrencies({ids: rowsSelected});        
        setCurrencies([ ...currencies.filter(it => !rowsSelected.includes(it.id)) ]);
        setRowsSelected([]);
        toast.info(answer.message);
      }
      finally {
        setSaving(false);
      }
    }

    const SelectedActions = () => {
      return (
        <div className={styles.actionButtons}>      
          <p>Выбрано {rowsSelected.length} элементов.</p>
          <button onClick={ () => handleOnOff(true) }>Активировать</button>
          <button onClick={ () => handleOnOff(false) }>Деактивировать</button>
          <button onClick={handleDelete}>Удалить</button>
        </div>
      );
    };

    useEffect(() => {
      const load = async() => {
        try {
          setLoading(true);
          const answer = (await fetchReserves()).data;
          setReserves(answer);          
        }
        finally {
          setLoading(false);
        }
      }
      if(!saving) load();
    }, [currencies, saving])

    if(saving) {
      return <p>Сохранение...</p>
    }

    if(loading || rolesLoading || !activeRole) {
      return <p>Загрузка...</p>
    }

    return (
      <>
      <h2>Список валют</h2>
      {activeRole.isEditCurrency && <button className={styles.addButton} onClick={() => { navigate(ROUTES.EDIT_CURRENCY); }}>Добавить..</button>}
      <TableView rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} activeRole={activeRole} reserves={reserves} setSaving={setSaving} />
      { rowsSelected.length > 0 && <SelectedActions /> }
      </>
    );
  }

  