package edu.uestc.eams.helper.domain.model

/** 登录后从 JWT / `blade-user/appInfo` 汇总的展示用资料（不含密码）。 */
data class UserProfile(
    val studentId: String,
    val displayName: String? = null,
    val bladeUserId: String? = null,
)
