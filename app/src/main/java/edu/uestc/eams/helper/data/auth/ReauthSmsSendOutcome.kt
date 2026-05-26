package edu.uestc.eams.helper.data.auth

/** 二次认证发码接口的解析结果。 */
data class ReauthSmsSendOutcome(
    val mobile: String? = null,
    /** 是否已发送：true 是，false 否，null 未知。 */
    val sent: Boolean? = null,
    val userMessage: String = "",
    val resendCooldownSec: Int? = null,
)
