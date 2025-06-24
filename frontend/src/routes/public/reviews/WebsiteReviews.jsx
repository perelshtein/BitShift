import { useState, useContext, useEffect } from "react";
import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";
import Header from "@/routes/public/modules/Header";
import Footer from "@/routes/public/modules/Footer";
import styles from "@/routes/public/public.module.css";
import StarRating from "@/routes/components/StarRating/StarRating";
import Pagination from "@/routes/components/Pagination";

export default function WebsiteReviews() {
    const [loading, setLoading] = useState(true);
    const { fetchPublicReviews } = useContext(WebsiteAPIContext);
    
    const [reviewsList, setReviewsList] = useState([]);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalReviewsCount, setTotalReviewsCount] = useState(0);    
    const rowsPerPage = 10;

    useEffect(() => {
        const loadReviews = async() => {
          try {
              const start = currentPage * rowsPerPage;
              const answer = (await fetchPublicReviews({start: start, count: rowsPerPage})).data;
              setTotalReviewsCount(answer.total);
              setReviewsList(answer.items);
          }
          finally {
              setLoading(false);
          }
        }
        loadReviews();
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

    const TableReviews = ({ data }) => {
        return (
          <>
            {data.map((row) => (
              <div className={styles.dataBar}>
                <h3>{row.caption}</h3>
                <StarRating value={row.rating} size="20" disabled />
                <p>
                  {new Date(row.date).toLocaleString()}
                  <p>{row.text}</p>
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
                    <h2 style={{marginBottom: 0}}>Отзывы</h2>
                    <TableReviews data={reviewsList} />
                    <Pagination
                      total={totalReviewsCount}
                      currentPage={currentPage}
                      rowsPerPage={rowsPerPage}
                      onPageChange={setCurrentPage}
                      className={styles.pagination}
                    />
                </main>

                <Footer styles={styles} />
            </div>
        </div>
    )
}