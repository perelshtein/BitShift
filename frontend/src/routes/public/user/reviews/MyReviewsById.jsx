import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";
import { useState, useContext, useEffect } from "react"
import { useParams, useNavigate } from "react-router-dom";
import Header from "@/routes/public/modules/Header";
import Footer from "@/routes/public/modules/Footer";
import styles from "@/routes/public/public.module.css";
import { reviewStatusList } from "@/routes/Index";
import { toast } from "react-toastify";
import { ROUTES } from "@/links";
import StarRating from "@/routes/components/StarRating/StarRating";

export default function MyReviewsById() {
    const { fetchUserReviewById, sendUserReview } = useContext(WebsiteAPIContext);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [review, setReview] = useState({text: ""});
    const { id } = useParams();
    const navigate = useNavigate();
    const [rating, setRating] = useState(0);

    const handleSave = async() => {
        try {
            setSaving(true);
            let request = { text: review.text, rating: review.rating };
            if(id) request = { ...request, id: id };
            let answer = await sendUserReview({review: request});
            toast.info(answer.message);
            !id && navigate(ROUTES.MY_REVIEWS);
        }
        finally {
            setSaving(false);
        }
    }

    useEffect(() => {
        const load = async() => {
            try {
                setLoading(true); 
                let answer = await fetchUserReviewById({id: id});
                setReview(answer.data);
            }  
            finally {
                setLoading(false);
            }
        }

        id && load();        
    }, [id]);

    if (loading || saving) {
        return (
            <div className={styles.website}>
                <Header styles={styles} />
                <div className={styles.modal}>
                    <div className={styles.spinner} />
                </div>
            </div>
        )
    }

    return (
        <div className={styles.websiteContainer}>
            <div className={styles.website}>
                <Header styles={styles} />
                <main>
                    <div className={styles.dataBar}>
                        <h3>{id ? `Отзыв id=${id}` : "Добавление отзыва"}</h3>
                        {review?.date && <p><b>Создан:</b> {new Date(review.date).toLocaleString()}</p>}
                        {review?.caption && <p><b>Заголовок:</b> {review.caption}</p>}
                        {review?.status && <p><b>Статус:</b> {reviewStatusList.find(it => it.id == review.status)?.name || review.status}</p>}
                        
                        <StarRating value={review?.rating} onChange={e => setReview({...review, rating: e})} size={40} />
                        <p>Оценка: {review?.rating}</p>

                        <textarea rows="10" cols="40" style={{ width: "100%" }} value={review.text} onChange={e => setReview({...review, text: e.target.value})} />
                        <div className={styles.twoColOptions} style={{marginTop: "2em"}}>
                            <button className={styles.save} onClick={handleSave}>Сохранить</button>
                            <a href={ROUTES.MY_REVIEWS}>&lt;&lt; К списку отзывов</a>
                        </div>
                    </div>                    
                </main>
                <Footer styles={styles} />
            </div>
        </div>
    )
}