import { useContext, useState } from "react";
import ComboList from "@/routes/components/ComboList";
import Nested from "../../components/Nested/Nested";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { CurrencyContext } from "@/context/CurrencyContext.jsx";
import { toast } from "react-toastify";
import styles from "../admin.module.css";

const ACTIONS = {
  MIN_AMOUNT: 1,
  MAX_AMOUNT: 2,
  PROFIT: 3,
  // AML: 4, // Uncomment if needed
};

const SelectedActions = ({ rowsSelected, setRowsSelected, directions, setDirections, setSaving }) => {
  const actions = [
    { id: ACTIONS.MIN_AMOUNT, name: "Мин сумма" },
    { id: ACTIONS.MAX_AMOUNT, name: "Макс сумма" },
    { id: ACTIONS.PROFIT, name: "Прибыль" },
    // {id: 4, name: "AML"},
  ];
  const { currencies, loading } = useContext(CurrencyContext);
  const { sendDirections } = useContext(AdminAPIContext);
  const [option, setOption] = useState(1);
  // const [currency, setCurrency] = useState(0);
  const [isVisible, setIsVisible] = useState(false);

  const updateDirections = async (updateFields) => {
    try {
      setSaving(true);
  
      // Prepare update payload with selected IDs and dynamic fields
      const payload = { ids: rowsSelected, ...updateFields };
      await sendDirections({ directions: payload });
  
      // Update state dynamically
      let updatedDirections = directions.map(c =>
        rowsSelected.includes(c.id) ? { ...c, ...updateFields } : c
      );
  
      // Extract direction names for toast
      const directionNames = rowsSelected
        .map(rowId => {
          let dir = directions.find(e => e.id == rowId)
          return `${dir?.from?.name || "N/A"} -> ${dir?.to?.name || "N/A"}`
        });
  
      setRowsSelected([]);
      setDirections(updatedDirections);
  
      // Generate toast message (same text for all updates)
      toast(`Направления обновлены: ${directionNames.join(", ")}`);
    } finally {
      setSaving(false);
    }
  };
  

  if (loading) {
    return <p>Загрузка..</p>
  }

  return (
    <div className={styles.selectedOrders} style={{ paddingBottom: "0" }}>
      <div style={{ marginBottom: "0.5em" }}>
        Выбрано {rowsSelected.length} элементов.
      </div>

      <div className={styles.horzList}>
        <button onClick={() => updateDirections({ isActive: true })}>Активировать</button>
        <button onClick={() => updateDirections({ isActive: false })}>Деактивировать</button>
        <button>Удалить</button>
      </div>

      <Nested title="Редактировать.." isVisible={isVisible} setIsVisible={setIsVisible}
        child={
          <div className={styles.directionFilter}>
            <ComboList rows={actions} selectedId={option} setSelectedId={setOption} />
            <EchoControl selected={option} currencyList={currencies} setSaving={setSaving} updateDirections={updateDirections} />
          </div>
        }        
        />

    </div>
  );
};

export default SelectedActions

function EchoControl({ selected, currencyList, setSaving, updateDirections }) {
  const [minSum, setMinSum] = useState(0);
  const [maxSum, setMaxSum] = useState(0);
  const [profit, setProfit] = useState(0);
  const [currencyMin, setCurrencyMin] = useState(0);
  const [currencyMax, setCurrencyMax] = useState(0);

  switch (Number(selected)) {
    case ACTIONS.MIN_AMOUNT:
      return (
        <>
          <input type="number" value={minSum} onChange={e => setMinSum(e.target.value)} />
          <ComboList rows={currencyList} selectedId={currencyMin} setSelectedId={setCurrencyMin} />
          <button id="save" onClick={() => updateDirections({ minSum: minSum, minSumCurrency: currencyMin })}>Сохранить</button>
        </>
      )
    case ACTIONS.MAX_AMOUNT:
      return (
        <>
          <input type="number" value={maxSum} onChange={e => setMaxSum(e.target.value)} />
          <ComboList rows={currencyList} selectedId={currencyMax} setSelectedId={setCurrencyMax} />
          <button id="save" onClick={() => updateDirections({ maxSum: maxSum, maxSumCurrency: currencyMax })}>Сохранить</button>
        </>
      )
    case ACTIONS.PROFIT:
      return (
        <>
          <input type="number" value={profit} onChange={e => setProfit(e.target.value)} />
          <span>%</span>
          <button className={styles.save} onClick={() => updateDirections({ profit: profit })}>Сохранить</button>
        </>
      )
    // case ACTIONS.AML:
    //   return(
    //     <>
    //     <label>
    //       <input type="checkbox" />
    //       Включить проверку
    //     </label>
    //     <button className={styles.save}>Сохранить</button>
    //     </>
    // )

    default:
      console.log(`Общие настройки: Не найден элемент с индексом ${selected}.`);
  }
}