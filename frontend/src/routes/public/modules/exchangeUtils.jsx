// Общий код для главной страницы и страницы создания заявки

export const updatePrice = (price, giveSelected, getSelected) => {  
    let result = "";
    if (price < 1) {
      let fidelity = giveSelected.fidelity;
      result = (1 / price).toFixed(fidelity);        
    } else {
      let fidelity = getSelected.fidelity;
      result = price.toFixed(fidelity);        
    }
    // Удалим дробную часть, если значение является целым числом
    result = result.replace(/\.0+$/, "");
      
    return price < 1
        ? `1 ${getSelected.code} = ${result} ${giveSelected.code}`
        : `1 ${giveSelected.code} = ${result} ${getSelected.code}`;
  };

export const updateReserveAndLimits = async (directionId, getSelected, fetchUserReserve, fetchUserDirection) => {
    if (!directionId || !getSelected) return;      
    const [reserveResult, limitResult] = await Promise.all([
        (fetchUserReserve({ to: getSelected.code })).then(result => result.data.reserve),
        (fetchUserDirection({ id: directionId })).then(result => result.data)
    ]);

    // Установим резерв
    const fidelity = getSelected.fidelity;    
    return {
        reserve: parseFloat(reserveResult).toFixed(fidelity).replace(/\.0+$/, ""),
        limit: limitResult
      };  
};

export const loadData = async (fetchCurrencies, fetchDirections, oldGiveId, oldGetId) => {
    const giveList = (await fetchCurrencies({ onlyGive: true })).data;
    const giveSelected = giveList?.find(it => it.id === oldGiveId) || giveList?.[0];
    const get = (await fetchDirections({ fromId: giveSelected.id })).data;
    const getList = get?.items?.map(it => ({ id: it.to.id, name: it.to.name, code: it.to.code, fidelity: it.to.fidelity })) || [];
    const getSelected = getList?.find(it => it.id === oldGetId) || getList?.[0];
    const directionId = get?.items?.find(it => it.to.id === getSelected.id)?.id;
    const price = get?.items?.find(it => it.to.id === getSelected.id)?.price;
  
    return { giveList, giveSelected, getList, getSelected, directionId, price };
  };

  export const loadDirectionsGive = async (e, currenciesGet, fetchDirections, giveSelectedParam) => {
    const getSelected = currenciesGet.find(it => it.id == e);
    const dirs = (await fetchDirections({ toId: e })).data;
    const giveList = dirs?.items?.map(it => ({ id: it.from.id, name: it.from.name, code: it.from.code, fidelity: it.from.fidelity })) || [];
    const giveSelected = giveList.find(item => item.id === giveSelectedParam.id) || giveList[0];
    const directionId = dirs?.items?.find(item => item.from.code == giveSelected.code).id;
    const price = dirs?.items?.find(item => item.from.code == giveSelected.code).price;
    return { giveList, giveSelected, getSelected, directionId, price };
  };
  
  export const loadDirectionsGet = async (e, currenciesGive, fetchDirections, getSelectedParam) => {
    const giveSelected = currenciesGive.find(it => it.id == e);
    const dirs = (await fetchDirections({ fromId: e })).data;
    const getList = dirs?.items?.map(it => ({ id: it.to.id, name: it.to.name, code: it.to.code, fidelity: it.to.fidelity })) || [];   
    const getSelected = getList.find(item => item.id === getSelectedParam.id) || getList[0];    
    const directionId = dirs?.items?.find(item => item.to.code == getSelected.code).id;
    const price = dirs?.items?.find(item => item.to.code == getSelected.code).price;
    return { getList, giveSelected, getSelected, directionId, price };
  };

  export const checkValues = (limit, reserve, giveAmount, getAmount, giveSelected, getSelected, setLimitsGiveWarn, setLimitsGetWarn) => {
    if(limit?.minSumGive > 0 && parseFloat(giveAmount) < limit?.minSumGive) {
      setLimitsGiveWarn(`Минимум ${limit.minSumGive.toFixed(giveSelected.fidelity)} ${giveSelected.code}`);
    }
    else if(limit?.maxSumGive > 0 && parseFloat(giveAmount) > limit?.maxSumGive) {
      setLimitsGiveWarn(`Максимум ${limit.maxSumGive.toFixed(giveSelected.fidelity)} ${giveSelected.code}`);
    }
    else setLimitsGiveWarn(null);

    let validMin = findValidMin(limit?.maxSumGet, reserve);
    if(limit?.minSumGet > 0 && parseFloat(getAmount) < limit?.minSumGet) {
      setLimitsGetWarn(`Минимум ${limit.minSumGet.toFixed(getSelected.fidelity)} ${getSelected.code}`);
    }
    else if(validMin && validMin < parseFloat(getAmount)) {
      setLimitsGetWarn(`Максимум ${validMin.toFixed(getSelected.fidelity)} ${getSelected.code}`);
    }
    else setLimitsGetWarn(null);
}

const findValidMin = (maxLimit, reserve) => {
  const values = [maxLimit, reserve].filter(val => val > 0); // Remove 0s
  return values.length > 0 ? Math.min(...values) : null; // Return min or disable check
};