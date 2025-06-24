export default function Pagination({ total, currentPage, rowsPerPage, onPageChange, className }) {
  const totalPages = Math.ceil(total / rowsPerPage);

  if (totalPages <= 1) return null; // No pagination needed

  return (
      <div className={className}>
          <button 
              disabled={currentPage === 0} 
              onClick={() => onPageChange(0)}
          >
              {"<<"} {/* Jump to first page */}
          </button>
          <button 
              disabled={currentPage === 0} 
              onClick={() => onPageChange(currentPage - 1)}
          >
              Назад
          </button>
          <span>Страница {currentPage + 1} из {totalPages}</span>
          <button 
              disabled={currentPage + 1 === totalPages} 
              onClick={() => onPageChange(currentPage + 1)}
          >
              Вперед
          </button>
          <button 
              disabled={currentPage + 1 === totalPages} 
              onClick={() => onPageChange(totalPages - 1)}
          >
              {">>"} {/* Jump to last page */}
          </button>
      </div>
  );
}

  