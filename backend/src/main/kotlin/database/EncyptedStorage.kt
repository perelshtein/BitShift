package com.github.perelshtein.database
import com.github.perelshtein.Options
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Base64

// для хранения ключей в базе в зашифрованном виде
class EncryptedStorage: KoinComponent {
    val options: Options by inject()
    val key = options.secureKey

    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES")
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(data.toByteArray()))
    }

    // Decryption Function
    fun decrypt(data: String): String {
        val cipher = Cipher.getInstance("AES")
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return String(cipher.doFinal(Base64.getDecoder().decode(data)))
    }
}