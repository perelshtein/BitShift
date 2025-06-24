export default function Pagination({ total, currentPage, rowsPerPage, onPageChange, styles }) {
    const totalPages = Math.ceil(total / rowsPerPage);
  
    if (totalPages <= 1) return null; // No pagination needed

    const handleClick = (e, page) => {
        e.preventDefault(); // Prevent <a> navigation
        onPageChange(page); // Trigger page change
    };
  
    // return (
    //     <div className={className}>
    //         <button 
    //             disabled={currentPage === 0} 
    //             onClick={() => onPageChange(currentPage - 1)}
    //         >
    //             Назад
    //         </button>
    //         <span>Страница {currentPage + 1} из {totalPages}</span>
    //         <button 
    //             disabled={currentPage + 1 === totalPages} 
    //             onClick={() => onPageChange(currentPage + 1)}
    //         >
    //             Вперед
    //         </button>
    //     </div>
    // );
    return (
        <div className={styles.pagination}>
            <a
                href="#" // Dummy href, prevented by JS
                className={`${styles.pageLink} ${styles.back} ${currentPage === 0 ? styles.disabled : ''}`}
                onClick={(e) => currentPage > 0 && handleClick(e, currentPage - 1)}
            >
                <img src="/big-arrow.svg" alt="Назад" className={styles.arrow} />
            </a>
            <span>Страница {currentPage + 1} из {totalPages}</span>
            <a
                href="#"
                className={`${styles.pageLink} ${styles.forward} ${currentPage + 1 === totalPages ? styles.disabled : ''}`}
                onClick={(e) => currentPage + 1 < totalPages && handleClick(e, currentPage + 1)}
            >
                <img src="/big-arrow.svg" alt="Вперед" className={styles.arrow} />
            </a>
        </div>
    );
  }