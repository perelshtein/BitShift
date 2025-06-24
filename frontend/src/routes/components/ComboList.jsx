  // Для корректной работы нужно передавать либо одну строку,
  // либо объект с полями id и name
  export default function ComboList({ rows, selectedId, setSelectedId }) {
    const handleChange = (e) => {  
      if (setSelectedId) {
        setSelectedId(e.target.value);
      }
    };
  
    return (
      <select onChange={handleChange} value={selectedId ?? undefined}>
        {rows.map((row) => (
          <option
            key={row.id ?? row}
            value={row.id ?? row}
          >
            {row.name ?? row}
          </option>
        ))}
      </select>
    );
  }
  
  