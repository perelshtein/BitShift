const SelectAllToggle = ({ items, rowsSelected, setRowsSelected }) => {
    const allSelected = rowsSelected.length === items.length && items.length > 0;
  
    const handleToggle = () => {
      if (allSelected) {
        setRowsSelected([]); // Deselect all
      } else {
        setRowsSelected(items.map(row => row.id)); // Select all
      }
    };
  
    return (
      <div>
        <button onClick={handleToggle}>
          {allSelected ? "Снять выделение" : "Выбрать все"}
        </button>
      </div>
    );
  };
  
  export default SelectAllToggle;
  