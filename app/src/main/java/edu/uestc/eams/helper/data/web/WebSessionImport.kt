package edu.uestc.eams.helper.data.web

import edu.uestc.eams.helper.data.eamsapp.EamsAppCookie
import edu.uestc.eams.helper.data.session.StoredCookie

/**
 * 校验内置浏览器「导入会话」是否来自移动教务且会话有效。
 */
object WebSessionImport {

    fun validate(pageUrl: String?, cookies: List<StoredCookie>): String? {
        val url = pageUrl?.trim().orEmpty().lowercase()
        if (url.isEmpty() || !url.contains("eamsapp.uestc.edu.cn")) {
            return "请先在移动教务网页完成登录后再导入。"
        }
        val hasJwt =
            cookies.any { c ->
                c.domain.contains("eamsapp.uestc.edu.cn", ignoreCase = true) &&
                    c.name.equals("JSESSIONID", ignoreCase = true) &&
                    EamsAppCookie.looksLikeJwt(c.value)
            }
        if (!hasJwt) {
            return "当前页面没有有效的移动教务登录信息，请确认网页里已登录成功。"
        }
        return null
    }
}
