import { ToastContainer } from "react-toastify";

export default function Footer({styles}) {
    return (
        <footer> 
            <div className={styles.contactLinks}>
                <a className={styles.contactsTelegram} href="tg://resolve?domain=e_bitshift">@e_bitshift</a>
                <a className={styles.contactsEmail} href="mailto:e@bitshift.su">e@bitshift.su</a>       
            </div>
        </footer>
    )
}