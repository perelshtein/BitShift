// конвертация
// более высокая комиссия (до 1%), но лучше подходит для штормов на рынке,
// т.к. цена фиксируется на 15 сек
// по сравн со spot (от 0,2%)
//проверим баланс валюты Б
            val balanceParams = mapOf("coin" to currency.uppercase(), "accountType" to "FUND")
            val balanceResult = get("/v5/asset/transfer/query-account-coin-balance", true, balanceParams).result
                ?: throw ValidationError("Не удалось проверить баланс ${currency} для отправки")
            val balanceData = json.decodeFromJsonElement<BalanceResponse>(balanceResult)
            val freeBalance = balanceData.balance?.firstOrNull { it.coin == currency.uppercase() }?.transferBalance?.toDoubleOrNull()
            if(freeBalance == null) throw ValidationError("Не удалось проверить баланс ${currency} для отправки")

            // если он меньше, чем объем перевода + комиссия, конвертируем USDT в валюту Б
            val minToSend = quantity + chainObj.fixedFee + quantity * chainObj.percentageFee
            if (freeBalance < minToSend) {
                val shortfall = minToSend - freeBalance
                log.info("Конвертируем USDT в ${currency} для отправки..")

                // Запросим конвертацию
                val quoteParams = TreeMap<String, Any>().apply {
                    put("accountType", "eb_convert_funding")
                    put("fromCoin", "USDT")
                    put("toCoin", currency.uppercase())
                    put("requestCoin", "USDT")
                    put("requestAmount", shortfall) //TODO: в USDT?
                }
                val quoteResult = post("/v5/asset/exchange/quote-apply", true, quoteParams).result
                    ?: throw ValidationError("Не удалось создать заявку на конвертацию USDT в $currency")
                val quote = json.decodeFromJsonElement<QuoteApplyResponse>(quoteResult)

                // подтвердим конвертацию
                val confirmParams = TreeMap<String, Any>().apply {
                    put("quoteTxId", quote.quoteTxId)
                }
                val confirmResult = post("/v5/asset/exchange/convert-execute", true, confirmParams).result
                    ?: throw ValidationError("Не удалось подтвердить заявку на конвертацию USDT в $currency")
                val confirm = json.decodeFromJsonElement<QuoteConfirmResponse>(confirmResult)

                // проверим конвертацию
                if (confirm.exchangeStatus == "success") {
                    log.info("Успешная конвертация ${quote.fromAmount} USDT в ${quote.toAmount} $currency")
                }
                else {
                    delay(5_000)
                    val answer = get("/v5/asset/exchange/convert-result-query", true, mapOf("quoteTxId" to quote.quoteTxId,
                        "accountType" to "eb_convert_funding")).result ?: throw ValidationError("Не удалось проверить конвертацию USDT в $currency")
                    val check = json.decodeFromJsonElement<QuoteCheckResponse>(answer)
                    if(check.result.exchangeTxId != quote.quoteTxId) throw ValidationError("Не удалось проверить конвертацию USDT в $currency")
                    if(check.result.exchangeStatus == "failure") throw ValidationError("Не удалось конвертировать USDT в $currency")
                    //TODO: добавить цикл, объем валюты
                }
            }
            
            @Serializable
data class BalanceResponse(val balance: List<CoinBalance>? = null) {
    @Serializable
    data class CoinBalance(val coin: String, val transferBalance: String)
}

@Serializable
data class QuoteApplyResponse(val quoteTxId: String, val exchangeRate: String, val fromAmount: String, val toAmount: String, val expiredTime: String)

@Serializable
data class QuoteConfirmResponse(val exchangeStatus: String)

@Serializable
data class QuoteCheckResponse(val result: Quote) {
    @Serializable
    data class Quote(val exchangeTxId: String, val fromCoin: String, val fromAmount: String, val toCoin: String,
         val toAmount: String, val exchangeStatus: String, val convertRate: String)
}
