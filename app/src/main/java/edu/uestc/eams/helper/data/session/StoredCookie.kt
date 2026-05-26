package edu.uestc.eams.helper.data.session

/**
 * Cookie 快照 DTO —— OkHttp ⇄ Android [android.webkit.CookieManager]。
 */
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAtMillis: Long?,
    val secure: Boolean,
    val httpOnly: Boolean,
)
