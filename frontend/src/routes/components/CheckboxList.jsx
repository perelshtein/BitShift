export default function CheckboxList({ rows, checkedKeys = [], onCheckChange, className }) {
    const normalizedRows = rows.map((row, index) =>
        typeof row === "string" ? { id: index, name: row } : row
      );

    const handleCheckboxChange = (id, isChecked) => {
        if (isChecked) {
            onCheckChange([...checkedKeys, id]);
        } else {
            onCheckChange(checkedKeys.filter((key) => key !== id));
        }
    };

    return (
        <ul className={className}>
            {normalizedRows.map((row) => (
            <li key={row.id}> 
                <label>
                    <input
                        type="checkbox"
                        value={row.id}
                        checked={checkedKeys?.includes(row.id)}
                        onChange={(e) => onCheckChange ? handleCheckboxChange(row.id, e.target.checked) : undefined }
                    />
                    {row.name}
                </label>
            </li>
            ))}
        </ul>
    )
}