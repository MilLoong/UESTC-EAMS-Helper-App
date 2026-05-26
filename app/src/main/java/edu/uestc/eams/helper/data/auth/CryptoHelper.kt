package edu.uestc.eams.helper.data.auth

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** CAS 登录密码 AES/CBC 加密。 */
object CryptoHelper {

    private val secureRandom = SecureRandom()

    private const val AES_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

    /** 生成 POST 字段 password 的密文。 */
    fun encryptLoginPassword(password: String, pwdEncryptSalt: String): String {
        val keyBytes = pwdEncryptSalt.trim().encodeToByteArray()
        require(keyBytes.size in setOf(16, 24, 32)) {
            "pwdEncryptSalt 长度需为 16/24/32 字节其一，当前=${keyBytes.size}"
        }

        val ivBytes = randomStringUtf8Bytes(16)
        val plaintext = randomStringUtf8Bytes(64) + password.encodeToByteArray()
        val padded = pkcs7Pad(plaintext, 16)

        // 手动 PKCS7；Cipher 使用 NoPadding，避免与填充重复。
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            IvParameterSpec(ivBytes),
        )
        val ciphertext = cipher.doFinal(padded)
        require(ciphertext.size == padded.size) {
            "AES 密文长度异常：cipher=${ciphertext.size} padded=${padded.size}"
        }
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun randomStringUtf8Bytes(length: Int): ByteArray =
        ByteArray(length) { AES_CHARS[secureRandom.nextInt(AES_CHARS.length)].code.toByte() }

    private fun pkcs7Pad(data: ByteArray, blockSize: Int): ByteArray {
        val pad = blockSize - (data.size % blockSize)
        return data + ByteArray(pad) { pad.toByte() }
    }
}
