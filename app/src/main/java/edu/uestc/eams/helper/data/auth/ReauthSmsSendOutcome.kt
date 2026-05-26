package edu.uestc.eams.helper.data.auth

/**
 * 对齐 Python `_reauth_send_code_outcome`：发码 JSON → 手机号、是否成功、展示文案、重发冷却秒数。
 *
 * [resendCooldownSec] 来自 JSON `codeTime` 或 `returnMessage` 里的[N 秒]；null/0 表示不启动客户端倒计时。
 */
data class ReauthSmsSendOutcome(
    val mobile: String? = null,
    /** true=已发送；false=失败（含 code_time_fail）；null=未知。 */
    val sent: Boolean? = null,
    val userMessage: String = "",
    val resendCooldownSec: Int? = null,
)
