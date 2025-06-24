import { useState, useEffect, useContext } from 'react';
import { WebsiteAPIContext } from "@/context/WebsiteAPIContext";

function MaintenanceWrapper({ children }) {
    const [isMaintenance, setIsMaintenance] = useState(false);
    const { fetchMaintenance } = useContext(WebsiteAPIContext);

    useEffect(() => {
        const checkMaintenance = async () => {
            let answer = (await fetchMaintenance()).data;
            setIsMaintenance(answer.isMaintenance);
        };
        checkMaintenance();
        const interval = setInterval(checkMaintenance, 60000); // Check every minute
        return () => clearInterval(interval);
    }, []);

    if (isMaintenance) {
        return (
            <div style={{
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                height: '100vh',
                backgroundColor: '#f8f8f8',
                fontFamily: 'Arial, sans-serif',
                textAlign: 'center',
                padding: '20px',
                margin: '0 auto'
            }}>
                <div>
                    <h1>Режим тех.обслуживания</h1>
                    <p>Мы проводим настройку сервера. Зайдите позже.</p>
                </div>
            </div>
        );
    }

    return children;
}

export default MaintenanceWrapper;