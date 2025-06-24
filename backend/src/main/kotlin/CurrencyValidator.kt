package com.github.perelshtein

import org.bitcoinj.core.Bech32
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.bouncycastle.jcajce.provider.digest.SHA256
import org.koin.core.component.KoinComponent
import java.lang.Integer.parseInt

// ERC-20 и BEP-20 проверяются одинаково
class CurrencyValidator: KoinComponent {
    //Задаем соответствие валюты и сети
    private val cryptoCurrencies = mapOf(
        "AUTO" to listOf(ChainType.AUTO),
        "RUB" to listOf(ChainType.CARDLUNA),
        "USD" to listOf(ChainType.CARDLUNA),
        "USDT" to listOf(ChainType.ERC20, ChainType.BEP20, ChainType.TRC20),
        "BTC" to listOf(ChainType.BTC),
        "BTCB" to listOf(ChainType.BTC),
        "BTG" to listOf(ChainType.BTC),
        "BSV" to listOf(ChainType.BTC),
        "BNB" to listOf(ChainType.BEP20),
        "BUSD" to listOf(ChainType.BEP20),
        "CAKE" to listOf(ChainType.BEP20),
        "ETH" to listOf(ChainType.ERC20),
        "DAI" to listOf(ChainType.ERC20, ChainType.BEP20),
        "LINK" to listOf(ChainType.ERC20),
        "UNI" to listOf(ChainType.ERC20),
        "MANA" to listOf(ChainType.ERC20),
        "SHIB" to listOf(ChainType.ERC20, ChainType.BEP20),
        "USDC" to listOf(ChainType.ERC20, ChainType.BEP20, ChainType.SOL),
        "TUSD" to listOf(ChainType.ERC20, ChainType.TRC20),
        "WBTC" to listOf(ChainType.ERC20), // + Mantle
        "WETH" to listOf(ChainType.ERC20, ChainType.SOL),
        "YFI" to listOf(ChainType.ERC20),
        "ZRX" to listOf(ChainType.ERC20),
        "SOL" to listOf(ChainType.SOL),
        "ADA" to listOf(ChainType.ADA)
    ).mapValues { (_, v) ->
        if (v.size > 1) listOf(ChainType.AUTO) + v else v
    }

    private val functions = mapOf(
        ChainType.CARDLUNA to ::checkCard,
        ChainType.ERC20 to ::checkErc20Bep20,
        ChainType.BEP20 to ::checkErc20Bep20,
        ChainType.TRC20 to ::checkBase58,
        ChainType.BTC to ::checkBTC,
        ChainType.SOL to ::checkSOL,
        ChainType.ADA to ::checkADA
    )

    val base58 = Base58()

    fun getSupportedCurrencies() = cryptoCurrencies.toSortedMap()
        .mapValues { (_, chains) ->
        chains.map { it.name }.sorted()
    }

    // рассчитаем хэш по алгоритму Keccak
    fun keccak256(input: String): String {
        val keccak = Keccak.Digest256()
        val hash = keccak.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) } //преобразуем массив байт в 16-ричную строку
    }

    // преобразуем обычный адрес в адрес с поддержкой контрольной суммы EIP-55
    // это тот же адрес, разница только в регистре символов,
    // которая и используется для валидации
    fun toEIP55Address(address: String): String {
        val cleanedAddress = address.removePrefix("0x").lowercase()
        val hash = keccak256(cleanedAddress)
        return cleanedAddress.mapIndexed { index, char ->
            if (!char.isDigit() && parseInt(hash[index].toString(), 16) >= 8) char.uppercaseChar() else char
        }.joinToString("", prefix = "0x")
    }

    fun checkEIP55(address: String): Boolean {
        val cleanedAddress = if(address.startsWith("0x")) address else "0x${address}"
        return cleanedAddress == toEIP55Address(address)
    }

    fun checkBase58(address: String): Boolean {
        runCatching {
            // Функция decode() может выбросить исключение, поэтому оборачиваем в try-catch
            val decoded = base58.decode(address)
            if (decoded.size != 25) return false

            // проверим префикс (0x41 в 16-ричном виде или 'T' в Base58)
            if (decoded[0] != 0x41.toByte()) return false

            // вытащим контрольную сумму из последних 4х байт
            val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)

            // Посчитаем контрольную сумму для первых 21 байт
            val sha256 = SHA256.Digest()
            val hash = sha256.digest(sha256.digest(decoded.copyOfRange(0, 21)))
            val calculatedChecksum = hash.copyOfRange(0, 4)

            // Compare the calculated checksum with the extracted checksum
            return checksum.contentEquals(calculatedChecksum)
        }.getOrElse {
            return false
        }
    }

    fun checkErc20Bep20(address: String): Boolean {
        val hexPart = address.removePrefix("0x")
        if (hexPart.length != 40) return false
        if(!hexPart.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return false
        if(hexPart.any { it.isLowerCase() } && hexPart.any { it.isUpperCase() } ) {
            return checkEIP55(address)
        }
        return true
    }

    fun checkADA(address: String): Boolean {
        return try {
            when {
                address.startsWith("addr1") || address.startsWith("stake1") -> {
                    // Bech32 validation for Shelley and stake addresses
                    Bech32.decode(address) != null
                }
                address.startsWith("Ae2") || address.startsWith("DdzFF") -> {
                    // Base58 validation for Byron addresses
                    base58.decode(address) != null
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun checkBTC(address: String): Boolean {
        val decoded = base58.decode(address)
        if (decoded.size != 25) return false

        val checksum = decoded.takeLast(4).toByteArray()
        val payload = decoded.dropLast(4).toByteArray()

        val sha256 = SHA256.Digest()
        val hash = sha256.digest(sha256.digest(payload))
        val calculatedChecksum = hash.take(4).toByteArray()

        return checksum.contentEquals(calculatedChecksum)
    }

    fun checkSOL(address: String): Boolean {
        if(address.length != 44) return false
        val decoded = base58.decode(address)
        return decoded.size == 32
    }

    // алгоритм Луна
    fun checkCard(address: String): Boolean {
        val cleanAddr = address.replace("\\s+".toRegex(), "")
        if(!cleanAddr.all { it.isDigit() }) return false

        val sum = cleanAddr.reversed().mapIndexed { index, char ->
            val digit = char.toString().toInt()
            if (index % 2 == 0) {
                if (digit * 2 > 9) digit * 2 - 9 else digit * 2
            } else {
                digit
            }
        }.sum()

        return sum % 10 == 0
    }

    fun checkAll(address: String): Boolean {
        return functions.values.any { it.invoke(address) }
    }

    fun checkAllChains(currency: String, address: String): Boolean {
        val chains = cryptoCurrencies[currency] ?: return false
        for (c in chains) {
            if(functions[c]?.invoke(address) == true) return true
        }
        return false
    }


    fun validate(currency: String, chainStr: String?, addr: String): Boolean {
        val address = addr.trim()
        val supportedChains = cryptoCurrencies[currency]
            ?: throw ValidationError("Валюта $currency не поддерживается")
        val chain = chainStr?.let { str ->
            enumValues<ChainType>().find { it.name == str.uppercase() }
        } ?: throw ValidationError("Сеть ${chainStr} не поддерживается")

        if(currency == "AUTO") return checkAll(address)
        if(chain == ChainType.AUTO) return checkAllChains(currency, address)

        //проверим конкретную валюту и сеть
        if (chain !in supportedChains) {
            throw ValidationError("Сеть ${chain.name} для валюты $currency не поддерживается")
        }
        return functions[chain]?.invoke(address) ?: false
    }
}

enum class ChainType {
    AUTO,
    CARDLUNA,
    ERC20, BEP20, TRC20,
    BTC, SOL, ADA
}