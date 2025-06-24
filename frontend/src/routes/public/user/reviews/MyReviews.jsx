import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";
import { AuthContext } from "@/context/AuthContext";
import { useState, useContext, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Header from "@/routes/public/modules/Header";
import Footer from "@/routes/public/modules/Footer";
import styles from "@/routes/public/public.module.css";
import TableView from "./Table";
import { ROUTES } from "@/links";
import { toast } from "react-toastify";
import { Warning } from "@/routes/Index";

export default function MyReviews() {
    const { fetchUserReviews, deleteUserReviews } = useContext(WebsiteAPIContext);
    const { userInfo } = useContext(AuthContext);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [reviews, setReviews] = useState([]);
    const navigate = useNavigate();
    const [rowsSelected, setRowsSelected] = useState([]);

    const handleAddReview = () => {
        navigate(ROUTES.MY_REVIEW_BY_ID);
    }

    const handleDelete = async() => {
        try {
            setSaving(true);
            let answer = await deleteUserReviews({ids: rowsSelected});
            toast.info(answer.message);
            load();            
        }
        finally {
            setSaving(false);
        }
    }
    
    const SelectedActions = () => {
        return (
          <div className={styles.actionButtons}>      
            <p>Выбрано {rowsSelected.length} элементов.</p>
            <button onClick={handleDelete}>Удалить</button>
          </div>
        );
      };

    const load = async() => {
        try {
            setLoading(true);            
            let answer = await fetchUserReviews({textSize: 15});
            setReviews(answer.data.items);
        }        
        finally {
            setLoading(false);
        }
    }

    useEffect(() => {        
        load();        
    }, []);

    if (loading | saving) {
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
                        <h3 style={{marginBottom: "1em"}}>Мои отзывы</h3>
                        {userInfo ? 
                        (<>
                        <TableView data={reviews} rowsSelected={rowsSelected} setRowsSelected={setRowsSelected} />
                        <div className={styles.twoColReviews}>                            
                            <button className={styles.save} style={{ margin: "1em 0", maxHeight: "2.5rem" }} onClick={handleAddReview}>Добавить отзыв..</button>                            
                            {rowsSelected.length > 0 && <SelectedActions />}
                        </div>
                        </>) : <Warning text="Для просмотра личных отзывов войдите на сайт с логином и паролем" /> }
                    </div>
                </main>
                <Footer styles={styles} />
            </div>
        </div>
    )
}