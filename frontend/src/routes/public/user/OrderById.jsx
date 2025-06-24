import { toast } from "react-toastify";
import { useContext, useState, useEffect, useRef } from "react";
import { useLocation } from "react-router-dom";
import { useParams } from "react-router-dom";
import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";
import styles from "@/routes/public/public.module.css";
import Header from "@/routes/public/modules/Header";
import Footer from "../modules/Footer";
import { orderStatusList } from "@/routes/Index";
import React from 'react';

export default function OrderById() {
    const { id } = useParams();
    const { state } = useLocation();  
    const { fetchUserOrder, sendTxId, deleteUserOrder } = useContext(WebsiteAPIContext);
    const [loading, setLoading] = useState(false);
    const [order, setOrder] = useState();
    const orderRef = useRef(null); // Store latest order
    const [timeLeft, setTimeLeft] = useState(null); // Countdown in seconds
    const [giveCurrency, setGiveCurrency] = useState();
    const [getCurrency, setGetCurrency] = useState();
    const [error, setError] = useState();
    const [txId, setTxId] = useState();
    const hasShownToast = useRef(false);
    
    const loadOrders = async() => {
        try {
            setLoading(true);     
            console.log("set true");
            const orderData = (await fetchUserOrder({id})).data;
            setOrder(orderData);            
            orderRef.current = orderData;
            
            setGiveCurrency(orderData.fromName);
            setGetCurrency(orderData.toName);
        }
        catch (e) {
            setError(e.message);
        }
        finally {
            setLoading(false);
            console.log("set false");
        }
    }

    const sendTransactionId = async() => {        
        const answer = await sendTxId({id: txId});
        toast.info(answer.message);        
    }

    const handleDelete = async() => {
        const answer = await deleteUserOrder({id});
        toast.info(answer.message);
        loadOrders();
    }

    const formatTimeLeft = (seconds) => {
      const mins = Math.floor(seconds / 60);
      const secs = seconds % 60;
      return `${mins}:${secs < 10 ? "0" + secs : secs}`;
    };

    useEffect(() => {
      loadOrders(); // Initial fetch            

      const pollInterval = setInterval(() => {
        if (orderRef.current && orderRef.current.isActive) {
          console.log("Обновляю статус заявки...");
          loadOrders(); // Always fetch the latest order
        } else {
          clearInterval(pollInterval); // Stop when completed
        }
      }, 30000);

      const updateCountdown = () => {
        if (orderRef.current && orderRef.current.dateStatusUpdated) {
          const updatedTime = new Date(orderRef.current.dateStatusUpdated).getTime();
          const now = Date.now();
          const deleteInterval = orderRef.current.deleteInterval * 60 || 1800; // 30 min = 1800s
          const secondsLeft = Math.max(0, deleteInterval - Math.floor((now - updatedTime) / 1000));
          setTimeLeft(secondsLeft);
        }
      };

      // Update countdown every second
      const countdownInterval = setInterval(updateCountdown, 1000);      

      return () => {
        clearInterval(pollInterval);
        clearInterval(countdownInterval);
      };
  }, [id]);

    if (error) {
      return (
          <div className={styles.website}>
          <Header styles={styles} />
          <main>
              <div className={styles.dataBar}>
                  <p>{error}</p>
              </div>
          </main>
          <footer />
          </div>
      )
    }

    if (loading || !order || !giveCurrency || !getCurrency) {
      return (
          <div className={styles.website}>
            <Header styles={styles} />
            <div className={styles.modal}>
                <div className={styles.spinner} />
            </div>
          </div>
      )
    }

    // const isCryptoPending = order.status === "waitingForConfirmation" || order.status === "waitingForPayment";  

    if (!hasShownToast.current && state?.messageType) {
      if (state.messageType === 'warning') {
        toast.warn(state.message);
      } else if (state.messageType === 'info') {
        toast.info(state.message);
      }
      hasShownToast.current = true; 
    }

    return (      
        <div className={styles.websiteContainer}>          
          <div className={styles.website}>
            <Header styles={styles} />
    
            <main>
              <div className={styles.dataBar}>                
                {state?.messageType === 'warning' && (
                  <div className={styles.warning}>
                    {state?.message}
                  </div>
                )}

                <h3>{order.isActive ? "Активная заявка" : "Закрытая заявка"}</h3>

              
                {!order.isActive 
                  ? null
                  : timeLeft !== null
                  ? <div className={styles.countdown}>Истекает через: {formatTimeLeft(timeLeft)}</div>
                  : <div className={styles.countdown}>Расчет времени...</div>
                }              

                <div className={styles.tableTwoCol}>
                  <div>Номер заявки:</div>
                  <div>{order.id}</div>

                  <div>Отдаю:</div>
                  <div>{order.amountFrom} {giveCurrency} {order.walletFrom ? `на счет ${order.walletFrom}` : ""}</div>

                  <div>Получаю:</div>
                  <div>{order.amountTo} {getCurrency} на счет {order.walletTo}</div>                

                  <div>Создана:</div>
                  <div>{new Date(order.dateCreated).toLocaleTimeString()}</div>

                  {order.dateUpdated && <>
                    <div>Обновлена:</div>
                    <div>{new Date(order.dateUpdated).toLocaleTimeString()}</div>
                  </>}

                  <div>Статус:</div>
                  <div>{orderStatusList.find(it => it.id == order.status)?.name || order.status}</div>

                  {order.requisites.size > 0 && (<>
                    <div>Реквизиты:</div>
                    <div>{order.requisites}</div>
                  </>)}                                                 
                </div>

                <div>
                  <AdditionalFields title="Дополнительные поля Отдаю" fields = {order.fieldsGive} />
                  <AdditionalFields title="Дополнительные поля Получаю" fields = {order.fieldsGet} />
                  {order.isActive && order.needsTxId &&
                  <div>
                    <input value={txId} onChange={e => setTxId(e.target.value)} className={styles.field} style={{marginRight: "1em"}} placeholder="Укажите txId, чтобы ускорить обработку заявки" />
                    <button onClick={sendTransactionId} >Отправить txId</button>
                  </div>}
                </div>

                {order.isActive &&
                    <button className={styles.sendButton} onClick={handleDelete}>Отменить заявку</button>
                }

              </div>        
            </main>           

            <Footer styles={styles} />
          </div>
        </div>
    )
}

function AdditionalFields({ title, fields }) {
  if (!fields || Object.keys(fields).length === 0) return null;

  return (
    <>
      <h4>{title}:</h4>
      <div className={styles.tableTwoCol}>
      {Object.entries(fields).map(([key, value]) => (
        <React.Fragment key={key}>
          <div>{key}</div>
          <div>{value}</div>
        </React.Fragment>
      ))}
      </div>
    </>
  );
}