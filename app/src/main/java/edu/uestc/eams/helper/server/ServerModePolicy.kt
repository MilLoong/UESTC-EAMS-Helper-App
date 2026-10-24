package edu.uestc.eams.helper.server

internal object ServerModePolicy {
    fun confirmed(mode: String, previous: String): String = when (mode) {
        "local", "server" -> mode
        else -> if (previous == "local") "local" else "server"
    }
}
