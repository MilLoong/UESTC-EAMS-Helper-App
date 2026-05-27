package edu.uestc.eams.helper.data.network

/** 用户主动拉取课表/成绩等时的可展示错误。 */
sealed class EamsFetchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** 无法连通 eamsapp，多为未在校内网；不清理本地登录。 */
    class OffCampus(
        cause: Throwable? = null,
    ) : EamsFetchException(
        "可能未在校内网环境，无法访问移动教务。请连接校园网或 VPN 后再试。登录状态已保留。",
        cause,
    )

    /** 校内网可达但会话无效；本地登录信息已清除。 */
    class SessionInvalid(
        cause: Throwable? = null,
    ) : EamsFetchException(
        "登录已失效，已清除本地登录信息，请重新登录或通过 Web 导入会话。",
        cause,
    )
}
