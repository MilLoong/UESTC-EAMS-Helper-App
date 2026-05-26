package edu.uestc.eams.helper.domain.model

/** 登录用户展示资料。 */
data class UserProfile(
    val studentId: String,
    val displayName: String? = null,
    val bladeUserId: String? = null,
)
