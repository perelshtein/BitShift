import { useState, useEffect, useContext } from "react";
import styles from "../admin.module.css";
import { AdminAPIContext } from "@/context/AdminAPIContext";
import { toast } from "react-toastify";

export default function StopWords() {
    const { fetchStopWords, sendStopWords } = useContext(AdminAPIContext);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [words, setWords] = useState();

    const load = async() => {
        try {
            setLoading(true);
            let result = (await fetchStopWords()).data;
            setWords(result ? result.join('\n') : '');
        }
        finally {
            setLoading(false);
        }
    }

    const handleSave = async() => {
        try {
            setSaving(true);
            // Convert newline-separated string to array, trim whitespace, and filter out empty strings
            const wordsArray = words
                .split('\n')
                .map((word) => word.trim())
                .filter((word) => word !== '');
            const result = await sendStopWords({ words: wordsArray });
            toast.info(result.message);
        }
        finally {
            setSaving(false);
        }
    }

    useEffect(() => {
        load();
    }, [])

    if(loading) {        
        return <p>Загрузка...</p>
    }
    if(saving) {
        return <p>Сохранение...</p>
    }

    return (
        <>
            <h3>Стоп-слова (с новой строки):</h3>
            <textarea rows="10" cols="40" value={words} style={{width: "100%"}} onChange={(e) => setWords(e.target.value)} />
            <button className={styles.save} style={{marginTop: "2em"}} onClick={handleSave}>Сохранить</button>
        </>
    )
}