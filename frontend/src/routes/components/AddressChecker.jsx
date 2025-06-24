import { useState, useEffect, useContext } from 'react';
import { CommonAPIContext } from "@/context/CommonAPIContext.jsx";

export default function AddressChecker({currencyId, wallet, setWallet, className, result, setResult}) {
  const { checkAddress } = useContext(CommonAPIContext);
  const [inputValue, setInputValue] = useState('');
  const [answer, setAnswer] = useState();

  // обновляем значение после N сек неактивности
  useEffect(() => {
    const handler = setTimeout(() => {
      setWallet(inputValue);
    }, 1500);

    // если пользов начал печатать, сбросим таймер
    return () => {
      clearTimeout(handler);
    };
  }, [inputValue]);

  //проверим адрес
  useEffect(() => {          
    const checkEnteredAddress = async() => {      
        let t = await checkAddress({id: currencyId, address: wallet.trim()});            
        setAnswer(t);
        setResult(t?.data?.isPassed);
    }

    if(wallet && wallet.trim().length > 0) checkEnteredAddress();
    else {
      setResult(null);
      setAnswer(null);
    }
  }, [wallet]);

  return (    
    <div>
      <input      
        type="text"
        value={inputValue}
        onChange={(e) => setInputValue(e.target.value)}
        placeholder="Адрес кошелька"  
        className={className}
        style = {answer?.data?.isPassed == false && {border: "2px solid red"} || {}}
      />    
      <p style={answer?.data?.isPassed == false ? {color: "red"} : answer?.data?.isPassed == true ? {color: "green"} : {}}>
      {answer?.message}
      </p>
      
    </div>
  );
}
