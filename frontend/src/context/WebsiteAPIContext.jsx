// вызовы к бэкенду для сайта

import { createContext } from 'react';
import { safeFetch } from "../ErrorHandling";
const SERVER_ROOT = import.meta.env.VITE_SERVER_ROOT;

export const WebsiteAPIContext = createContext();

export function WebsiteAPIProvider({ children }) {
    const fetchUserReserve = async({to}) => {
        const params = new URLSearchParams({ to });
        const url = `${SERVER_ROOT}/api/user/reserve?${params.toString()}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchUserDirection = async({id}) => {
        const url = `${SERVER_ROOT}/api/user/direction/${id}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const sendUserOrder = async({body}) => {
        const url = `${SERVER_ROOT}/api/user/order`;
        return await safeFetch(url, {
            method: 'PUT',
            body: JSON.stringify(body),
            headers: {
                "Content-Type": "application/json"
            },
        })
    }

    const claimTempOrder = async({id}) => {
        const url = `${SERVER_ROOT}/api/user/order/claim`;
        return await safeFetch(url, {
            method: 'PUT',
            body: JSON.stringify({tempOrderId: id}),
            headers: {
                "Content-Type": "application/json"
            },
        })
    }

    const fetchUserOrder = async({id}) => {
        const url = `${SERVER_ROOT}/api/user/order/${id}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchUserOrders = async({start, count} = {}) => {        
        const params = new URLSearchParams();
        if(start) params.append('start', start);
        if(count) params.append('count', count);
        const url = `${SERVER_ROOT}/api/user/orders?${params.toString()}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const sendUserInfo = async({user}) => {
        const url = `${SERVER_ROOT}/api/user`;
        return await safeFetch(url, {
            method: 'POST',
            body: JSON.stringify(user),
            headers: {
                "Content-Type": "application/json"
            },
        })
    }

    const sendTxId = async({id}) => {
        const url = `${SERVER_ROOT}/api/user/order/txid`;
        return await safeFetch(url, {
            method: 'PUT',
            body: JSON.stringify({txId: id}),
            headers: {
                "Content-Type": "application/json"
            },
        })
    }

    const deleteUserOrder = async({id}) => {
        const url = `${SERVER_ROOT}/api/user/order/${id}`;
        return await safeFetch(url, {
            method: 'DELETE'
        })
    }

    const fetchUserReviews = async({textSize} = {}) => {
        let params = new URLSearchParams();
        if(textSize) params.append("textSize", textSize);
        const url = `${SERVER_ROOT}/api/user/reviews?${params.toString()}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchUserReviewById = async({id}) => {
        const url = `${SERVER_ROOT}/api/user/reviews/${id}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const sendUserReview = async({review}) => {
        const url = `${SERVER_ROOT}/api/user/reviews`;
        return await safeFetch(url, {
            method: review.id ? 'POST' : 'PUT',
            body: JSON.stringify(review),
            headers: {
                "Content-Type": "application/json"
            },
        })
    }

    const deleteUserReviews = async({ids}) => {
        const params = new URLSearchParams({ ids });
        const url = `${SERVER_ROOT}/api/user/reviews?${params.toString()}`;
        return await safeFetch(url, {
            method: 'DELETE'
        })
    }

    const fetchPublicReviews = async({start, count, textSize}) => {
        let params = new URLSearchParams();
        if(start) params.append("start", start);
        if(count) params.append("count", count);
        if(textSize) params.append("textSize", textSize);        
        const url = `${SERVER_ROOT}/api/publicReviews?${params.toString()}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchPublicReviewById = async({id}) => {
        const url = `${SERVER_ROOT}/api/publicReviews/${id}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchUserReferrals = async({start, count, dateStart, dateEnd}) => {
        let params = new URLSearchParams();
        if(start) params.append("start", start);
        if(count) params.append("count", count);
        if(dateStart) params.append("dateStart", dateStart);
        if(dateEnd) params.append("dateEnd", dateEnd);
        const url = `${SERVER_ROOT}/api/user/referrals_orders?${params.toString()}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchUserReferralsStatus = async() => {
        const url = `${SERVER_ROOT}/api/user/referrals_status`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchUserCashback = async({start, count, dateStart, dateEnd}) => {
        let params = new URLSearchParams();
        if(start) params.append("start", start);
        if(count) params.append("count", count);
        if(dateStart) params.append("dateStart", dateStart);
        if(dateEnd) params.append("dateEnd", dateEnd);
        const url = `${SERVER_ROOT}/api/user/cashback_orders?${params.toString()}`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const fetchUserCashbackStatus = async() => {
        const url = `${SERVER_ROOT}/api/user/cashback_status`;
        return await safeFetch(url, {
            method: 'GET'
        })
    }

    const sendRefId = async({rid}) => {
        console.log("Запрашиваю cookie для реферала с id = ", rid);
        const url = `${SERVER_ROOT}/generateRefCookie`;
        return await safeFetch(url, {
            method: 'POST',
            body: JSON.stringify({rid}),
            headers: {
                "Content-Type": "application/json"
            },
        })
    }

    const sendUserCashbackWithdraw = async() => {
        const url = `${SERVER_ROOT}/api/user/cashback_withdraw`;
        return await safeFetch(url, {
            method: 'POST'
        })
    }

    const sendUserReferralsWithdraw = async() => {
        const url = `${SERVER_ROOT}/api/user/referrals_withdraw`;
        return await safeFetch(url, {
            method: 'POST'
        })
    }

    const fetchMaintenance = async () => {
        return await safeFetch(`${SERVER_ROOT}/api/maintenance`, {
            method: 'GET',
        });
    };

    return(
        <WebsiteAPIContext.Provider value={{
            fetchUserReserve, fetchUserDirection, sendUserOrder, claimTempOrder, fetchUserOrder,
            fetchUserOrders, sendUserInfo, sendTxId, deleteUserOrder,
            fetchUserReviews, fetchUserReviewById, sendUserReview, deleteUserReviews,
            fetchPublicReviews, fetchPublicReviewById,
            fetchUserReferrals, fetchUserReferralsStatus, sendUserReferralsWithdraw,
            fetchUserCashback, fetchUserCashbackStatus, sendUserCashbackWithdraw,
            sendRefId, fetchMaintenance
        }}>
            {children}
        </WebsiteAPIContext.Provider>
    )
}