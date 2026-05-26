package edu.uestc.eams.helper.data.session

/**
 * Cookie 快照，用于 OkHttp 与 WebView 互相同步。
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
