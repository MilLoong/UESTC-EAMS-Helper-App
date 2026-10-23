package edu.uestc.eams.helper.server

import java.net.URI

object ServerOrigin {
    fun validate(raw: String, localDev: Boolean): String {
        val origin = raw.trim().trimEnd('/')
        val uri = URI(origin)
        val nums = uri.host.orEmpty().split('.').map { it.toIntOrNull() }
        val lan = nums.size == 4 && nums.all { it != null && it in 0..255 } &&
            (nums[0] == 10 || (nums[0] == 172 && nums[1]!! in 16..31) || (nums[0] == 192 && nums[1] == 168))
        require((uri.scheme == "https" || (localDev && uri.scheme == "http" && lan)) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.path.orEmpty().isEmpty() && uri.query == null && uri.fragment == null &&
            (uri.port == -1 || uri.port in 1..65535)) { "需要根 HTTPS 地址，或明确确认的局域网 HTTP 地址" }
        return origin
    }
}
