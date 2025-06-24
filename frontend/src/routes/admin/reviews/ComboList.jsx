export default function ComboList({ rows, selectedId, setSelectedId, name }) {
    const handleChange = (e) => {
        if (setSelectedId) {
            if (name) setSelectedId({ name, value: e.target.value });
            else setSelectedId(e.target.value);
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