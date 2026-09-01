package edu.uestc.eams.helper.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 应用主题选择：持久化当前主题 key，并作为可观察状态供 Compose 切换。 */
class ThemePreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _theme =
        MutableStateFlow(prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME)
    val theme: StateFlow<String> = _theme.asStateFlow()

    fun setTheme(name: String) {
        val key = name.trim().ifBlank { DEFAULT_THEME }
        prefs.edit().putString(KEY_THEME, key).apply()
        _theme.value = key
    }

    companion object {
        const val DEFAULT_THEME = "blue"
        private const val PREF_NAME = "app_theme"
        private const val KEY_THEME = "theme_name"
    }
}
