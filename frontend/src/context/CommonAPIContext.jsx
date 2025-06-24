// общие вызовы к бэкенду для админки и сайта

import { createContext } from 'react';
import { safeFetch } from "../ErrorHandling";
const SERVER_ROOT = import.meta.env.VITE_SERVER_ROOT;

export const CommonAPIContext = createContext();

export function CommonAPIProvider({ children }) {
    const fetchNews = async ({start, count, textSize}) => {
        const params = new URLSearchParams();
        if(start) params.append("start", start);
        if(count) params.append("count", count);
        if(textSize) params.append("textSize", textSize);
        const url = `${SERVER_ROOT}/api/news?${params.toString()}`;
        return await safeFetch(url, {
            method: 'GET',
        });
    };

    const fetchNewsRecord = async ({id}) => {
        const url = `${SERVER_ROOT}/api/news/${id}`;
        return await safeFetch(url, {
            method: 'GET',
        });
    };

    const fetchCurrencies = async ({onlyGive, onlyGet} = {}) => {
        let params = new URLSearchParams();
        if (onlyGive) params.append('onlyGive', onlyGive);        
        if (onlyGet) params.append('onlyGet', onlyGet);        
        return await safeFetch(`${SERVER_ROOT}/api/currencies?${params.toString()}`, {
            method: 'GET',
        });
    };

    const fetchFieldsForCurrency = async ({id}) => {
        const url = `${SERVER_ROOT}/api/currency/${id}/fields`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchCurrencyFields = async () => {
        return await safeFetch(`${SERVER_ROOT}/api/currencyFields`, {
            method: 'GET',
        });
    };

    const checkAddress = async ({id, address}) => {        
        const url = `${SERVER_ROOT}/api/currency/${id}/validate/${address}`;
        return await safeFetch(url, {
            method: 'GET'
        })                
    }

    const fetchDirections = async ({start, count, filter, status, fromId, toId}) => {
        const params = new URLSearchParams();
        if(start) {
            params.append('start', start);
        }
        if(count) {
            params.append('count', count);
        }
        if (filter) {
            params.append('filter', filter);
        }
        if (status) {
            params.append('status', status);
        }
        if (fromId) {
            params.append('fromId', fromId);
        }
        if (toId) {
            params.append('toId', toId);
        }
        const url = `${SERVER_ROOT}/api/directions?${params.toString()}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchDirection = async ({id}) => {
        const url = `${SERVER_ROOT}/api/direction/${id}`;
        return await safeFetch(url, {
            method: 'GET'            
        })
    }

    const fetchFormula = async ({from, to}) => {
        const params = new URLSearchParams({ from, to });
        const url = `${SERVER_ROOT}/api/formula?${params.toString()}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchDescription = async ({id}) => {
        const url = `${SERVER_ROOT}/api/description/${id}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchOrderStatus = async ({id}) => {
        const url = `${SERVER_ROOT}/api/orderStatus/${id}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchReserves = async () => {
        const url = `${SERVER_ROOT}/api/reserves`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchReserve = async({code}) => {
        const url = `${SERVER_ROOT}/api/reserve/${code}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const getDirectionId = async({from, to}) => {
        const params = new URLSearchParams({ from, to });
        const url = `${SERVER_ROOT}/api/findDirectionId?${params.toString()}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    return(
        <CommonAPIContext.Provider value={{        
            fetchNews, fetchNewsRecord,
            fetchCurrencies, fetchFieldsForCurrency, fetchCurrencyFields,
            checkAddress,
            fetchDirections, fetchDirection, fetchFormula,
            fetchDescription, fetchOrderStatus,
            fetchReserves, fetchReserve, getDirectionId
        }}>
            {children}
        </CommonAPIContext.Provider>
    )
}