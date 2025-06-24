package com.github.perelshtein

import java.math.BigInteger

class Base58 {
    val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun encode(input: ByteArray): String {
        var bigInt = BigInteger(1, input)
        val result = StringBuilder()
        while (bigInt > BigInteger.ZERO) {
            val remainder = (bigInt % BigInteger.valueOf(58)).toInt()
            result.append(BASE58_ALPHABET[remainder])
            bigInt /= BigInteger.valueOf(58)
        }

        // Add '1' for each leading 0 byte
        for (byte in input) {
            if (byte.toInt() == 0) {
                result.append('1')
            } else {
                break
            }
        }

        return result.reverse().toString()
    }

    fun decode(input: String): ByteArray {
        var bigInt = BigInteger.ZERO
        for (char in input) {
            val index = BASE58_ALPHABET.indexOf(char)
            if (index == -1) throw ValidationError("Invalid Base58 character: $char")
            bigInt = bigInt * BigInteger.valueOf(58) + BigInteger.valueOf(index.toLong())
        }

        // Convert bigInt to byte array
        val result = bigInt.toByteArray()

        // Remove sign byte (if any)
        val leadingZeros = input.takeWhile { it == '1' }.count()
        val trimmedResult = result.copyOfRange(if (result[0].toInt() == 0) 1 else 0, result.size)

        // Prepend the leading zeros from the original Base58 string
        return ByteArray(leadingZeros + trimmedResult.size) { index ->
            if (index < leadingZeros) 0 else trimmedResult[index - leadingZeros]
        }
    }
}