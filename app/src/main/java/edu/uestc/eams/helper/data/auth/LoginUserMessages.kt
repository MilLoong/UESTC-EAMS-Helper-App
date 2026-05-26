package edu.uestc.eams.helper.data.auth

/** 登录错误转用户可读文案。 */
object LoginUserMessages {

    fun fromThrowable(t: Throwable?): String {
        val raw = t?.message?.trim().orEmpty()
        if (raw.isEmpty()) return "登录失败，请稍后重试。"
        val low = raw.lowercase()
        return when {
            "短信" in raw || "验证码" in raw || "dynamiccode" in low -> raw.take(120)
            "图形" in raw || "验证码" in raw && "短信" !in raw -> "需要图形验证码，请点右上角 Web 在网页中登录。"
            "超时" in raw || "timeout" in low -> "连接超时，请检查网络后重试，或使用右上角 Web 登录。"
            "探针" in raw || "会话" in raw && "失效" in raw -> "登录状态无效，请重新登录。"
            "jwt" in low || "移动教务" in raw -> "未能完成移动教务登录，请用 Web 登录后 导入会话。"
            "学号" in raw && "密码" in raw -> "学号或密码不正确，请核对后重试。"
            "口令" in raw || "密码" in raw -> "学号或密码不正确，请核对后重试。"
            "二次认证" in raw -> "短信验证未通过或已过期，请重新获取验证码。"
            raw.length <= 80 && !looksTechnical(raw) -> raw
            else -> "登录未成功，请检查账号密码与网络；仍失败请用右上角 Web 登录。"
        }
    }

    private fun looksTechnical(msg: String): Boolean {
        val low = msg.lowercase()
        val needles =
            listOf(
                "http",
                "castgc",
                "tgt",
                "execution",
                "get ",
                "post ",
                "jar",
                "jwtlen",
                "probe",
                "okhttp",
                "ticket",
                "302",
                "cipherbytes",
                "encLen",
                "bfp",
                "idas",
                "抓包",
                "fallback",
            )
        return needles.any { it in low }
    }
}
