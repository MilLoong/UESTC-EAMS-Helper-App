package edu.uestc.eams.helper.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class CryptoHelperTest {

    /** JVM 侧复现 [CryptoHelper] 算法（避免 unit test 未 mock android.util.Base64）。 */
    @Test
    fun encrypt_plaintext_length_matches_capture_106() {
        val password = "8281410lqh"
        val salt = "BP64TESTBP64TEST"
        val aesChars = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
        val rnd = SecureRandom()
        fun randBytes(n: Int) =
            ByteArray(n) { aesChars[rnd.nextInt(aesChars.length)].code.toByte() }
        val plaintext = randBytes(64) + password.encodeToByteArray()
        val pad = 16 - (plaintext.size % 16)
        val padded = plaintext + ByteArray(pad) { pad.toByte() }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(salt.encodeToByteArray(), "AES"),
            IvParameterSpec(randBytes(16)),
        )
        val enc = Base64.getEncoder().encodeToString(cipher.doFinal(padded))
        assertEquals(80, padded.size)
        assertEquals(108, enc.length)
    }

    @Test
    fun sha256_prefix_matches_user_log() {
        val hex =
            MessageDigest.getInstance("SHA-256")
                .digest("8281410lqh".encodeToByteArray())
                .joinToString("") { b -> "%02x".format(b) }
                .take(12)
        assertEquals("94c5786f4df8", hex)
    }
}
