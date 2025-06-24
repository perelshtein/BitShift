import { useState } from "react";

// Раскрывающийся блок с настройками
export default function Nested({title, child, isVisible, setIsVisible, styles }) {
    // const [isVisible, setIsVisible] = useState(false);
    const toggleVisibility = () => {
      setIsVisible(!isVisible);
    };
  
    return (
      <>        
        <h3 style={{width: "calc-size(max-content, size + 2em)"}} onClick={toggleVisibility}
          className={isVisible ? styles.show : styles.hide}>{title}</h3>
        <div className={styles.nested}>
          {child}
        </div>        
      </>
    )
  }