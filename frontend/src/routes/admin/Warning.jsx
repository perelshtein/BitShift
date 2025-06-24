import styles from "./admin.module.css";


export default function Warning({ message }) {
    if (!message) return null;
    return (
        <p className={styles.validationWarning}>{message}</p>
    );
}