package edu.uestc.eams.helper.data.auth

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 对齐 Python `encrypt_password(password, salt)`（见 H3CoF6/uestc-login `utils.py`）：
 * key = Utf8(trim(salt)), IV = random 16 ASCII from [AES_CHARS],
 * plaintext = random 64 ASCII + UTF-8 密码，AES/CBC/PKCS7，Base64(密文)。
 */
object CryptoHelper {

    private val secureRandom = SecureRandom()

    /** 与 Python `AES_CHARS` 一致。 */
    private const val AES_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

    /** 明文密码字段（POST `password=`），CAS 服务端按页盐解密。 */
    fun encryptLoginPassword(password: String, pwdEncryptSalt: String): String {
        val keyBytes = pwdEncryptSalt.trim().encodeToByteArray()
        require(keyBytes.size in setOf(16, 24, 32)) {
            "pwdEncryptSalt 长度需为 16/24/32 字节其一，当前=${keyBytes.size}"
        }

        val ivBytes = randomStringUtf8Bytes(16)
        val plaintext = randomStringUtf8Bytes(64) + password.encodeToByteArray()
        val padded = pkcs7Pad(plaintext, 16)

        /*
         * 对齐 Python/CryptoJS：`AES/CBC` + 手动 PKCS7，**禁止** PKCS5Padding（会与手动 pad 叠成 96 字节→128 b64）。
         */
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
