// вызовы к бэкенду для админки

import { createContext } from 'react';
import { safeFetch } from "../ErrorHandling";
const SERVER_ROOT = import.meta.env.VITE_SERVER_ROOT;

export const AdminAPIContext = createContext();

export function AdminAPIProvider({ children }) {

  const sendNews = async ({news}) => {
    return await safeFetch(`${SERVER_ROOT}/api/news`, {
      method: news.id ? 'POST' : 'PUT',
      body: JSON.stringify(news),
      headers: {
        "Content-Type": "application/json"
      },
    })
  };

  const deleteNews = async ({ids}) => {
    const params = new URLSearchParams({ ids });
    const url = `${SERVER_ROOT}/api/news?${params.toString()}`;
    return await safeFetch(url, {
      method: 'DELETE'
    })
  };

  const fetchOptions = async () => {
    return await safeFetch(`${SERVER_ROOT}/api/options`, {
      method: 'GET',
    });
  };

  const sendOptions = async ({opt}) => {
    return await safeFetch(`${SERVER_ROOT}/api/options`, {
      method: 'POST',
      body: JSON.stringify(opt),
      headers: {
        "Content-Type": "application/json"
      },
    })
  };

  const fetchUserInfo = async ({userId}) => {
    const url = `${SERVER_ROOT}/api/users/${userId}`;
    return await safeFetch(url, {
      method: 'GET',
    });
  }

  const fetchUsers = async ({start, count, query, roleId}) => {
    const params = new URLSearchParams({ start, count });
    if (roleId !== null && roleId !== undefined) {
      params.append("roleId", roleId);
    }
    if (query) {
      params.append("query", query);
    }
    const url = `${SERVER_ROOT}/api/users?${params.toString()}`;
    return await safeFetch(url, {
      method: 'GET',
    });
  };

  //редактируем пользов или создаем нового
  const sendUser = async ({user}) => {
    return await safeFetch(`${SERVER_ROOT}/api/users`, {
      method: user.id ? 'POST' : 'PUT',
      body: JSON.stringify(user),
      headers: {
        "Content-Type": "application/json"
      },
    })
  };

  const deleteUsers = async ({ids}) => {
    const params = new URLSearchParams({ ids });
    const url = `${SERVER_ROOT}/api/users?${params.toString()}`;
    return await safeFetch(url, {
      method: 'DELETE'
    })
  };

  const fetchRoles = async () => {
    return await safeFetch(`${SERVER_ROOT}/api/roles`, {
      method: 'GET',
    });
  };

  const sendRole = async ({role}) => {
    return await safeFetch(`${SERVER_ROOT}/api/roles`, {
      method: role.id ? 'POST' : 'PUT',
      body: JSON.stringify(role),
      headers: {
        "Content-Type": "application/json"
      },
    })
  };

  const deleteRoles = async ({ids}) => {
    const params = new URLSearchParams({ ids });
    const url = `${SERVER_ROOT}/api/roles?${params.toString()}`;
    return await safeFetch(url, {
      method: 'DELETE'
    })
  };

  const sendCurrency = async ({currency}) => {
    return await safeFetch(`${SERVER_ROOT}/api/currency`, {
      method: currency.id ? 'POST' : 'PUT',
      body: JSON.stringify(currency),
      headers: {
        "Content-Type": "application/json"
      },
    })
  };

  const deleteCurrencies = async ({ids}) => {
    const params = new URLSearchParams({ ids });
    const url = `${SERVER_ROOT}/api/currencies?${params.toString()}`;
    return await safeFetch(url, {
      method: 'DELETE'
    })
  };

  const sendCurrencyField = async ({field}) => {
    return await safeFetch(`${SERVER_ROOT}/api/currencyField`, {
      method: field.id ? 'POST' : 'PUT',
      body: JSON.stringify(field),
      headers: {
        "Content-Type": "application/json"
      },
    })
  };

  const deleteCurrencyFields = async ({ids}) => {
    const params = new URLSearchParams({ ids });
    const url = `${SERVER_ROOT}/api/currencyFields?${params.toString()}`;
    return await safeFetch(url, {
      method: 'DELETE'
    })
  };

  const fetchCurrenciesForField = async ({id}) => {
    const url = `${SERVER_ROOT}/api/currencyField/${id}/currencies`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const sendCurrenciesForField = async ({id, giveCurrencies, getCurrencies}) => {
    const url = `${SERVER_ROOT}/api/currencyField/${id}/currencies`;
    return await safeFetch(url, {
      method: 'POST',
      body: JSON.stringify({ give: giveCurrencies, get: getCurrencies }),
      headers: {
        "Content-Type": "application/json"
      },
    })
  }

  const sendFieldsForCurrency = async ({id, giveFields, getFields}) => {
    const url = `${SERVER_ROOT}/api/currency/${id}/fields`;
    return await safeFetch(url, {
      method: 'POST',
      body: JSON.stringify({ give: giveFields, get: getFields }),
      headers: {
        "Content-Type": "application/json"
      },
    })
  }

  const fetchExchange = async ({exchangeName}) => {
    const url = `${SERVER_ROOT}/api/exchanges/${exchangeName}`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const sendExchange = async ({exchange}) => {
    return await safeFetch(`${SERVER_ROOT}/api/exchanges/${exchange.name}`, {
      method: 'POST',
      body: JSON.stringify(exchange),
      headers: {
        "Content-Type": "application/json"
      },
    })
  }

  // Обновить курсы на бэке прямо сейчас
  const updateCourses = async ({exchangeName}) => {
    return await safeFetch(`${SERVER_ROOT}/api/exchanges/${exchangeName}/courses/update`, {
      method: 'POST',
    })
  }

  const fetchCourses = async ({start, count, filter, exchange}) => {
    const params = new URLSearchParams({ start, count });
    if (filter) {
      params.append('filter', filter);
    }
    if (exchange) {
      params.append('exchange', exchange);
    }
    const url = `${SERVER_ROOT}/api/courses?${params.toString()}`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const sendDirection = async ({direction}) => {
    return await safeFetch(`${SERVER_ROOT}/api/direction`, {
      method: direction.id ? 'POST' : 'PUT',
      body: JSON.stringify(direction),
      headers: {
        "Content-Type": "application/json"
      },
    })
  }

  //массовое обновление направлений
  const sendDirections = async ({directions}) => {
    return await safeFetch(`${SERVER_ROOT}/api/directions`, {
      method: 'POST',
      body: JSON.stringify(directions),
      headers: {
        "Content-Type": "application/json"
      },
    })
  }

  const fetchFormulas = async ({start, count, status, filter}) => {
    const params = new URLSearchParams({ start, count });
    if (status) {
      params.append('status', status);
    }
    if (filter) {
      params.append('filter', filter);
    }
    const url = `${SERVER_ROOT}/api/formulas?${params.toString()}`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const fetchFormulaVariants = async ({from, to, searchCase}) => {
    console.log(from, to);
    const params = new URLSearchParams({ from, to });
    if (searchCase) {
      params.append('calcMode', searchCase);
    }
    const url = `${SERVER_ROOT}/api/formulas/generate?${params.toString()}`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const sendFormula = async ({formula}) => {
    const url = `${SERVER_ROOT}/api/formula`;
    return await safeFetch(url, {
      method: formula.id ? 'POST' : 'PUT',
      body: JSON.stringify(formula),
      headers: {
        "Content-Type": "application/json"
      },
    })
  }

  const deleteFormulas = async ({ids}) => {
    const params = new URLSearchParams({ ids });
    const url = `${SERVER_ROOT}/api/formulas?${params.toString()}`;
    return await safeFetch(url, {
      method: 'DELETE'
    })
  }

  const fetchDescriptions = async () => {
    const url = `${SERVER_ROOT}/api/descriptions`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const sendDescription = async ({description}) => {
    const url = `${SERVER_ROOT}/api/description`;
    return await safeFetch(url, {
      method: description.id ? 'POST' : 'PUT',
      body: JSON.stringify(description),
      headers: {
        "Content-Type": "application/json"
      },
    })
  }

  const deleteDescriptions = async ({ids}) => {
    const params = new URLSearchParams({ ids });
    const url = `${SERVER_ROOT}/api/descriptions?${params.toString()}`;
    return await safeFetch(url, {
      method: 'DELETE'
    })
  };

  const fetchOrderStatuses = async () => {
    const url = `${SERVER_ROOT}/api/orderStatuses`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const deleteOrderStatuses = async ({ids}) => {
    const params = new URLSearchParams({ ids });
    const url = `${SERVER_ROOT}/api/orderStatuses?${params.toString()}`;
    return await safeFetch(url, {
      method: 'DELETE'
    })
  };

  const sendOrderStatus = async ({orderStatus}) => {
    const url = `${SERVER_ROOT}/api/orderStatus`;
    return await safeFetch(url, {
      method: orderStatus.id ? 'POST' : 'PUT',
      body: JSON.stringify(orderStatus),
      headers: {
        "Content-Type": "application/json"
      },
    })
  }

  const sendReserve = async ({baseCurrency, reserve}) => {
    const url = `${SERVER_ROOT}/api/reserve/${baseCurrency}`;
    return await safeFetch(url, {
      method: 'POST',
      body: JSON.stringify(reserve),
      headers: {
        "Content-Type": "application/json"
      },
    })
  }

  const reloadReserve = async ({baseCurrency}) => {
    const url = `${SERVER_ROOT}/api/reserve/update/${baseCurrency}`;
    return await safeFetch(url, {
      method: 'POST'
    })
  }

  const fetchUsersNotify = async () => {
    const url = `${SERVER_ROOT}/api/notify/users`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const sendUsersNotify = async ({notify}) => {
    const url = `${SERVER_ROOT}/api/notify/users`;
    return await safeFetch(url, {
      method: 'POST',
      body: JSON.stringify(notify),
      headers: {
        "Content-Type": "application/json"
      }
    })
  }

  const fetchAdminNotify = async () => {
    const url = `${SERVER_ROOT}/api/notify/admin`;
    return await safeFetch(url, {
      method: 'GET'
    })    
  }

  const sendAdminNotify = async ({notify}) => {
    const url = `${SERVER_ROOT}/api/notify/admin`;
    return await safeFetch(url, {
      method: 'POST',
      body: JSON.stringify(notify),
      headers: {
        "Content-Type": "application/json"
      }
    })
  }

  const fetchCurrencyValidators = async () => {
    const url = `${SERVER_ROOT}/api/currencies/getValidators`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const fetchOrders = async ({start, count, status, filter, userId, fromId, toId, dateStart, dateEnd, id}) => {
    const params = new URLSearchParams();
    const options = { start, count, status, filter, userId, fromId, toId, dateStart, dateEnd, id };
    Object.entries(options).forEach(([key, value]) => {
      if (value != null) params.append(key, value);
    });
    const url = `${SERVER_ROOT}/api/orders?${params.toString()}`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const fetchOrder = async({id}) => {
    const url = `${SERVER_ROOT}/api/order/${id}`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const sendOrders = async ({ ids, status, isActive, requisites, profit, walletFrom, rateGive, rateGet}) => {
    const url = `${SERVER_ROOT}/api/order`;
    const definedParams = Object.fromEntries(
      Object.entries({ ids, status, isActive, requisites, profit, walletFrom, rateGive, rateGet })
        .filter(([_, value]) => value !== undefined)
    );
    
    return await safeFetch(url, {
      method: 'POST',
      body: JSON.stringify(definedParams),
      headers: {
        "Content-Type": "application/json"
      },
    })
  }

  const fetchPayin = async ({exchange}) => {
    const params = new URLSearchParams({ exchange });
    const url = `${SERVER_ROOT}/api/payin?${params.toString()}`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const fetchPayout = async ({exchange}) => {
    const params = new URLSearchParams({ exchange });
    const url = `${SERVER_ROOT}/api/payout?${params.toString()}`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const fetchReviews = async({start, count, dateStart, dateEnd, userName, userMail, status, text, textSize, rating} = {}) => {    
    const params = new URLSearchParams();
    Object.entries({ start, count, dateStart, dateEnd, userName, userMail, status, text, textSize, rating })
      .filter(([_, value]) => value !== undefined)
      .forEach(([key, value]) => params.append(key, value));
    const url = `${SERVER_ROOT}/api/reviews?${params.toString()}`;
    return await safeFetch(url, {
      method: 'GET'
    })    
  }

  const fetchReview = async({id}) => {
    const url = `${SERVER_ROOT}/api/reviews/${id}`;
    return await safeFetch(url, {
      method: 'GET'
    })
  }

  const sendReview = async({review}) => {
    const url = `${SERVER_ROOT}/api/reviews`;    
    return await safeFetch(url, {
      method: 'POST',
      body: JSON.stringify(review),
      headers: {
          "Content-Type": "application/json"
      },
    })
  }

  const fetchStopWords = async() => {
    const url = `${SERVER_ROOT}/api/stopwords`;
    return await safeFetch(url, {
      method: 'GET'
    })    
  }

  const sendStopWords = async({words}) => {
    const url = `${SERVER_ROOT}/api/stopwords`;
    return await safeFetch(url, {
      method: 'POST',
      body: JSON.stringify(words),
      headers: {
        "Content-Type": "application/json"
      },
    })    
  }

  const fetchReferralsStatus = async({start, count, dateStart, dateEnd, refMail}) => {
      const params = new URLSearchParams();
      Object.entries({ start, count, dateStart, dateEnd, refMail })
        .filter(([_, value]) => value !== undefined)
        .forEach(([key, value]) => params.append(key, value));
      const url = `${SERVER_ROOT}/api/referrals_status?${params.toString()}`;
      return await safeFetch(url, {
        method: 'GET'
      })
  }

  const fetchReferralsOrders = async({start, count, dateStart, dateEnd, userMail}) => {
      const params = new URLSearchParams();
      Object.entries({ start, count, dateStart, dateEnd, userMail })
        .filter(([_, value]) => value !== undefined)
        .forEach(([key, value]) => params.append(key, value));
      const url = `${SERVER_ROOT}/api/referrals_orders?${params.toString()}`;
      return await safeFetch(url, {
        method: 'GET'
      })      
  }

  const fetchCashbackStatus = async({start, count, dateStart, dateEnd, userMail}) => {
      const params = new URLSearchParams();
      Object.entries({ start, count, dateStart, dateEnd, userMail })
        .filter(([_, value]) => value !== undefined)
        .forEach(([key, value]) => params.append(key, value));
      const url = `${SERVER_ROOT}/api/cashback_status?${params.toString()}`;
      return await safeFetch(url, {
        method: 'GET'
      })      
  }

  const fetchCashbackOrders = async({start, count, dateStart, dateEnd, userMail}) => {
      const params = new URLSearchParams();
      Object.entries({ start, count, dateStart, dateEnd, userMail })
        .filter(([_, value]) => value !== undefined)
        .forEach(([key, value]) => params.append(key, value));
      const url = `${SERVER_ROOT}/api/cashback_orders?${params.toString()}`;
      return await safeFetch(url, {
        method: 'GET'
      })      
  }

  const sendReferralsConfirm = async({rid}) => {
    const url = `${SERVER_ROOT}/api/referrals_paid`;
    return await safeFetch(url, {
      method: 'POST',
      body: JSON.stringify({rid: rid}),
      headers: {
          "Content-Type": "application/json"
      }
    })    
  }

  const sendCashbackConfirm = async({userId}) => {
    const url = `${SERVER_ROOT}/api/cashback_paid`;
    return await safeFetch(url, {
      method: 'POST',
      body: JSON.stringify({userId: userId}),
      headers: {
          "Content-Type": "application/json"
      }
    })    
  }

  return (<AdminAPIContext.Provider value={{
      sendNews, deleteNews,
      fetchOptions, sendOptions,
      fetchUserInfo, fetchUsers, sendUser, deleteUsers,
      fetchRoles, sendRole, deleteRoles,
      sendCurrency, deleteCurrencies,
      sendCurrencyField, deleteCurrencyFields, fetchCurrenciesForField, sendCurrenciesForField, sendFieldsForCurrency,
      fetchExchange, sendExchange,
      updateCourses, fetchCourses,
      sendDirection, sendDirections,
      fetchFormulas, fetchFormulaVariants, sendFormula, deleteFormulas,
      fetchDescriptions, sendDescription, deleteDescriptions,
      fetchOrderStatuses, deleteOrderStatuses, sendOrderStatus,
      fetchOrders, sendOrders, fetchOrder,
      sendReserve, reloadReserve,
      fetchUsersNotify, sendUsersNotify, fetchCurrencyValidators,
      fetchAdminNotify, sendAdminNotify,
      fetchPayin, fetchPayout,
      fetchReviews, fetchReview, sendReview,
      fetchStopWords, sendStopWords,
      fetchReferralsStatus, fetchReferralsOrders, sendReferralsConfirm,
      fetchCashbackStatus, fetchCashbackOrders, sendCashbackConfirm
    }}>
    { children }
    </AdminAPIContext.Provider>
  )
}