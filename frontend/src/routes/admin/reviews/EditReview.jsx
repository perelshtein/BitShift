import { AdminAPIContext } from "@/context/AdminAPIContext";
import { useState, useContext, useEffect } from "react"
import { useParams, useNavigate } from "react-router-dom";
import ComboList from "@/routes/components/ComboList";
import styles from "../admin.module.css";
import { reviewStatusList } from "@/routes/Index";
import { toast } from "react-toastify";
import { ROUTES } from "@/links";
import StarRating from "@/routes/components/StarRating/StarRating";

export default function EditReview() {    
  const { id } = useParams();
  const [rowsSelected, setRowsSelected ] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [review, setReview] = useState();
  const { fetchReview, sendReview } = useContext(AdminAPIContext);

  const load = async() => {
    try {
        setLoading(true);  
        let answer = await fetchReview({id: id});
        setReview(answer.data);
    }        
    finally {
        setLoading(false);
    }
  }

  const handleSave = async() => {
    try {
        setSaving(true);  
        let answer = await sendReview({review: review});
        toast.info(answer.message);
    }
    finally {
        setSaving(false);
    }
  }

  useEffect(() => {        
    load();        
  }, []);
  
  if (loading) {
      return <p>Загрузка...</p>
  }

  if (saving) {
      return <p>Сохранение...</p>
  }

  return(
      <>
      <h2>Редактировать отзыв {id}</h2>
      <div className={styles.tableTwoColLayout}>
        <div>Дата:</div>
        {review?.date && new Date(review?.date).toLocaleString()}

        <div>Имя:</div>
        {review?.caption}

        <label htmlFor="mail">Почта:</label>
        {review?.mail}

        <label htmlFor="review">Текст отзыва:</label>
        <textarea rows="10" cols="40" id="review" value={review?.text} onChange={(e) => setReview({...review, text: e.target.value})} />

        <label htmlFor="status">Статус:</label>
        <ComboList id="status" rows={reviewStatusList} selectedId={review?.status} setSelectedId={(value) => setReview({...review, status: value})} />

        <label htmlFor="rating">Оценка:</label>
        <StarRating id="rating" value={review?.rating} onChange={e => setReview({...review, rating: e})} size={30} />

        <button className={styles.save} style={{marginTop: "2em"}} onClick={handleSave}>Сохранить</button>
      </div>
      </>
  )
}