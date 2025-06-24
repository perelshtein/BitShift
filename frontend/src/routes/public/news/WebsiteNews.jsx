import { useState, useContext, useEffect } from "react";
import { CommonAPIContext } from "@/context/CommonAPIContext";
import Header from "@/routes/public/modules/Header";
import Footer from "@/routes/public/modules/Footer";
import styles from "@/routes/public/public.module.css";
import { ROUTES } from "@/links";

export default function WebsiteNews() {
    const [loading, setLoading] = useState(true);
    const { fetchNews } = useContext(CommonAPIContext);
    
    const [newsList, setNewsList] = useState([]);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalNewsCount, setTotalNewsCount] = useState(0);    
    const rowsPerPage = 10;

    useEffect(() => {
        const loadNews = async() => {
        try {
            const start = currentPage * rowsPerPage;
            const answer = await fetchNews({start: start, count: rowsPerPage, textSize: 150});            
            setNewsList(answer.data.items);
            setTotalNewsCount(answer.data.total);
        }
        finally {
            setLoading(false);
        }
        }
        loadNews();
    }, [currentPage]);

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

    const TableNews = ({ data }) => {
        return (
          <>
            {data.map((row, index) => (
              <div key={index} className={styles.dataBar}>
                <h3>{row.caption}</h3>
                {new Date(row.date).toLocaleString()}
                <p>{row.text}</p>
                <p className={styles.detailLink}>
                    <a href={`${ROUTES.WEBSITE_NEWS}/${row.id}`}>Подробнее...</a>
                </p>
              </div>
            ))}
          </>
        );
      };

    return(            
        <div className={styles.websiteContainer}>
            <div className={styles.website}>
                <Header styles={styles} />
        
                <main>                    
                    <h2 style={{marginBottom: 0}}>Новости</h2>
                    <TableNews data={newsList} />
                </main>

                <Footer styles={styles} />
            </div>
        </div>
    )
}