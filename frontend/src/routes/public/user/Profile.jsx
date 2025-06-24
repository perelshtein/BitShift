import { toast } from "react-toastify";
import { useContext, useState, useEffect } from "react";
import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";
import { AuthContext } from "@/context/AuthContext";
import Header from "@/routes/public/modules/Header";
import Footer from "@/routes/public/modules/Footer";
import styles from "@/routes/public/public.module.css";
import { Warning } from "@/routes/Index";

export default function Profile() {
    const { userInfo, setUserInfo } = useContext(AuthContext);
    const { sendUserInfo } = useContext(WebsiteAPIContext);
    const [user, setUser] = useState(userInfo);
    const [saving, setSaving] = useState(false);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setUser(prev => ({
            ...prev || {},
            [name]: value,
        }));
    }

    const handleSave = async () => {
        try {
            setSaving(true);
            let result = await sendUserInfo({ user: user });
            setUserInfo(user);
            toast.info(result);
        }
        finally {
            setSaving(false);
        }
    }

    useEffect(() => {
        if (!user && userInfo) { // Only set if user is unset
            setUser(userInfo);
        }
    }, [userInfo]);

    if (saving) {
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
                        <h3>Профиль</h3>

                        {userInfo ?
                        (<>
                            <div className={styles.twoColOptions}>
                                <label htmlFor="name">Имя:</label>
                                <input name="name" value={user?.name} onChange={handleChange} />

                                <label htmlFor="mail">Почта:</label>
                                <input name="mail" value={user?.mail} onChange={handleChange} />

                                <label htmlFor="password">Пароль:</label>
                                <input name="password" value={user?.password} onChange={handleChange} />
                            </div>
                            <button className={styles.save} style={{ margin: "2em 0 1em" }} onClick={handleSave} >Сохранить</button>
                        </>) :
                        <Warning text="Для просмотра профиля войдите на сайт с логином и паролем" />
                        }
                    </div>
                </main>
                <Footer styles={styles} />
            </div>
        </div>
    )
}