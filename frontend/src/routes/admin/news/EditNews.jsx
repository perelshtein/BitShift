import { useState, useEffect, useContext } from "react";
import { toast } from 'react-toastify';
import { useParams, useNavigate } from "react-router-dom";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import { CommonAPIContext } from "@/context/CommonAPIContext.jsx";
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { ROUTES } from "@/links";
import styles from "../admin.module.css";

export default function EditNews() {  
    const { id } = useParams();
    const [news, setNews] = useState({
      caption: "",
      text: ""
    });
    const [loading, setLoading] = useState(id);
    const [saving, setSaving] = useState(false);
    const { fetchNewsRecord } = useContext(CommonAPIContext);
    const { sendNews } = useContext(AdminAPIContext);
    const { activeRole, loading: roleLoading } = useContext(UsersRolesContext); 
    const navigate = useNavigate();

    const handleSave = async() => {
      try {
        setSaving(true);
        const updatedNews = {
          ...(parseInt(id) ? { id: parseInt(id) } : {}),
          ...news
        }
        const answer = await sendNews({news: updatedNews});
        toast.info(answer.message);
        navigate(ROUTES.NEWS);
      }
      finally {
        setSaving(false);
      }
    }

    useEffect(() => {
      const loadNewsRecord = async() => {
        try {
          const newsRecord = (await fetchNewsRecord({id: id})).data;
          setNews(newsRecord);
        }
        finally {
          setLoading(false);
        }
      }
      if(id) loadNewsRecord();
    }, []);

    if (loading || roleLoading) {
      return <p>Загрузка...</p>;
    }
  
    if (saving) {      
      return <p>Сохранение...</p>;
    }

    const firstWord = activeRole.isEditNews ? "Редактировать" : "Просмотреть";
    return(
        <>        
        {id ? <h2>{firstWord} новость</h2> : <h2>Создать новость</h2>}
        <div>
            <input style={{ width: "70%" }} value={news.caption} onChange={e => setNews({...news, caption: e.target.value})} disabled={!activeRole.isEditNews} />
        </div>
        <div>
            <textarea rows="10" cols="40" style={{ width: "70%" }} value={news.text} onChange={e => setNews({...news, text: e.target.value})} disabled={!activeRole.isEditNews} />
        </div>
        {activeRole.isEditNews && <button className={styles.save} onClick={handleSave}>Сохранить</button>}
        </>
    )
}