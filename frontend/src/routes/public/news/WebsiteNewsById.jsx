import { useState, useContext, useEffect } from "react";
import { useParams } from "react-router-dom";
import { CommonAPIContext } from "@/context/CommonAPIContext";
import Header from "@/routes/public/modules/Header";
import Footer from "@/routes/public/modules/Footer";
import styles from "@/routes/public/public.module.css";
import { ROUTES } from "@/links";
import { useNavigate } from "react-router-dom";

export default function WebsiteNewsById() {
    const [loading, setLoading] = useState(true);
    const { fetchNewsRecord } = useContext(CommonAPIContext);    
    const [news, setNews] = useState();
    const { id } = useParams();
    const navigate = useNavigate();

    useEffect(() => {
        const loadNews = async() => {
        try {
            const news = await fetchNewsRecord({id: id});
            setNews(news.data);            
        }
        finally {
            setLoading(false);
        }
        }
        loadNews();
    }, [id]);

    const handleBackClick = (e) => {
        e.preventDefault();
        if (window.history.length > 1) {
            navigate(-1);
        } else {        
            navigate(ROUTES.WEBSITE);
        }
    };
    

    if (loading) {
        return (
          <div className={styles.website}>
            <Header styles={styles} />
            <div className={styles.modal}>
              <div className={styles.spinner} />
            </div>       
          </div>
        )
    }    

    return(
        <div className={styles.websiteContainer}>
            <div className={styles.website}>
                <Header styles={styles} />
        
                <main>                    
                    <h2 style={{marginBottom: 0}}>{news.caption}</h2>
                    <div className={styles.dataBar}>
                        {new Date(news.date).toLocaleString()}
                        <p dangerouslySetInnerHTML={{ __html: news.text }}></p>          
                        <div className={styles.detailLink}>                            
                            <a href="#" onClick={handleBackClick}>Назад</a>
                        </div>                        
                    </div>
                </main>

                <Footer styles={styles} />
            </div>
        </div>
    )
}