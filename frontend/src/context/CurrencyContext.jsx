import { createContext, useState, useEffect, useContext } from "react";
import { AuthContext } from "./AuthContext";
import { CommonAPIContext } from "./CommonAPIContext";

export const CurrencyContext = createContext();

export function CurrencyProvider({ children }) {  
  const [currencies, setCurrencies] = useState([]);
  const [currencyFields, setCurrencyFields] = useState([]);
  const [loading, setLoading] = useState(true);
  const { loggedIn } = useContext(AuthContext);
  const { fetchCurrencies, fetchCurrencyFields } = useContext(CommonAPIContext);

  useEffect(() => {
    const loadData = async () => {
      if(!loggedIn) {
        return;
      }            
      try {
          setLoading(true);
          const currencyData = await fetchCurrencies();
          setCurrencies(currencyData.data);
          const currencyFieldData = await fetchCurrencyFields();
          setCurrencyFields(currencyFieldData.data);     
      } finally {
          setLoading(false);
      }
    };    
    loadData();
  },[loggedIn]);

  return (    
    <CurrencyContext.Provider value={{currencies, setCurrencies, currencyFields, setCurrencyFields, loading}}>      
      {children}
    </CurrencyContext.Provider>
  );
}
