package ss.colytitse.setappfull

import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Xml
import io.github.libxposed.service.XposedService
import androidx.core.content.edit
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Configuration store. Values are kept in libxposed Remote Preferences (group "config") so the
 * hook (which runs in the target app / system_server process) can read them through
 * `getRemotePreferences("config")`. When the service is not bound (e.g. running outside LSPosed),
 * it transparently falls back to a local SharedPreferences so the UI still works standalone.
 */
object AppSettings {

    const val CONFIG_NAME = "config"

    const val SYSTEM_VIEW = 0
    const val USER_VIEW = 1
    const val MODE_1 = 1
    const val MODE_2 = 2
    const val NO_SET = -1

    private const val KEY_SYSTEM_MODE = "SystemMode"
    private const val KEY_APP_MODE = "AppMode"
    private const val KEY_SCOPE_MODE = "scope_mode_switch"
    private const val KEY_SWITCH_LIST = "onSwitchListView"
    private const val KEY_HELLO_WORLD = "hello_world"
    private const val KEY_SHOW_ICON = "show_launcher_icon"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_THEME = "app_theme"
    private const val LAUNCHER_ALIAS = "ss.colytitse.setappfull.MainActivityLauncher"

    /** The active preferences: remote (LSPosed) when available, otherwise local. */
    fun prefs(context: Context): SharedPreferences {
        val remote = App.getService()?.getRemotePreferences(CONFIG_NAME)
        return remote ?: context.getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE)
    }

    // ---------------------------------------------------------------------------------- system mode

    private fun stringSet(prefs: SharedPreferences, key: String): Set<String> =
        try {
            prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
        } catch (_: ClassCastException) {
            // 旧版本存的是 #pkg# 字符串，忽略（重新勾选即可）
            emptySet()
        }

    private fun systemModeSet(prefs: SharedPreferences): Set<String> =
        stringSet(prefs, KEY_SYSTEM_MODE)

    private fun appModeSet(prefs: SharedPreferences): Set<String> =
        stringSet(prefs, KEY_APP_MODE)

    fun deleteSelection(context: Context, packageName: String) {
        val prefs = prefs(context)
        prefs.edit {
            putStringSet(KEY_SYSTEM_MODE, systemModeSet(prefs) - packageName)
                .putStringSet(KEY_APP_MODE, appModeSet(prefs) - packageName)
        }
    }

    fun clearSelections(context: Context, packages: Set<String>) {
        val prefs = prefs(context)
        prefs.edit {
            putStringSet(KEY_SYSTEM_MODE, systemModeSet(prefs) - packages)
                .putStringSet(KEY_APP_MODE, appModeSet(prefs) - packages)
        }
    }

    fun saveSystemMode(context: Context, packageName: String) {
        val prefs = prefs(context)
        prefs.edit {
            putStringSet(KEY_APP_MODE, appModeSet(prefs) - packageName)
                .putStringSet(KEY_SYSTEM_MODE, systemModeSet(prefs) + packageName)
        }
    }

    fun saveAppMode(context: Context, packageName: String) {
        val prefs = prefs(context)
        val set = appModeSet(prefs)
        if (packageName in set) return
        prefs.edit { putStringSet(KEY_APP_MODE, set + packageName) }
    }

    fun getSetMode(context: Context, packageName: String): Int {
        val prefs = prefs(context)
        return when {
            packageName in appModeSet(prefs) -> MODE_1
            packageName in systemModeSet(prefs) -> MODE_2
            else -> NO_SET
        }
    }

    // ---------------------------------------------------------------------------------- ui prefs

    fun getSwitchListType(context: Context): Int =
        prefs(context).getInt(KEY_SWITCH_LIST, USER_VIEW)

    fun saveSwitchList(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_SWITCH_LIST, value) }
    }

    fun setScopeMode(context: Context, scopeMode: Boolean) {
        prefs(context).edit { putBoolean(KEY_SCOPE_MODE, scopeMode) }
    }

    fun getScopeMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCOPE_MODE, true)

    fun getHelloWorld(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HELLO_WORLD, true)

    fun setHelloWorld(context: Context) {
        prefs(context).edit { putBoolean(KEY_HELLO_WORLD, false) }
    }

    // ---------------------------------------------------------------------------------- launcher icon

    fun getShowLauncherIcon(context: Context): Boolean =
        context.getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_ICON, true)

    fun setShowLauncherIcon(context: Context, show: Boolean) {
        context.getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_SHOW_ICON, show) }
        applyLauncherIcon(context)
    }

    fun applyLauncherIcon(context: Context) {
        val state = if (getShowLauncherIcon(context)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, LAUNCHER_ALIAS),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    // ---------------------------------------------------------------------------------- language

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguages.SYSTEM_CODE) ?: AppLanguages.SYSTEM_CODE

    fun setLanguage(context: Context, code: String) {
        context.getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_LANGUAGE, code) }
        // 不在这里同步 applicationLocales（会触发配置变更导致闪屏），
        // 由 App.onCreate 与 MainActivity.onResume 统一同步。
    }

    /** 应用启动时调用，确保语言设置生效（API 33+ 通过 LocaleManager）。 */
    fun applyLanguage(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            manager.applicationLocales = if (getLanguage(context) == AppLanguages.SYSTEM_CODE) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(getLanguage(context))
            }
        }
    }

    // ---------------------------------------------------------------------------------- theme

    const val THEME_SYSTEM = "system"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    const val THEME_SYSTEM_MONET = "system_monet"
    const val THEME_DARK_MONET = "dark_monet"
    const val THEME_LIGHT_MONET = "light_monet"

    fun getTheme(context: Context): String =
        context.getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_SYSTEM_MONET) ?: THEME_SYSTEM_MONET

    fun setTheme(context: Context, mode: String) {
        context.getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_THEME, mode) }
    }

    /** 根据指定语言 code 创建带对应 locale 的 context（Compose 层即时切换语言用）。 */
    fun localizedContext(base: Context, code: String): Context {
        // 「跟随系统」也创建明确的系统 locale context，而不是直接返回 base：
        // API 33+ 下 base.resources 反映 applicationLocales，切回「跟随系统」时其更新是异步的，
        // 直接返回 base 会让界面停留在上一语言甚至不刷新。
        val locale = if (code == AppLanguages.SYSTEM_CODE) {
            AppLanguages.systemLocale(base)
        } else {
            Locale.forLanguageTag(code)
        }
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** API 33 以下：在 attachBaseContext 里用此方法覆盖 locale。 */
    fun wrapLocale(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        return localizedContext(base, getLanguage(base))
    }

    // ---------------------------------------------------------------------------------- export / import

    fun exportConfig(context: Context, outputStream: OutputStream) {
        val all = prefs(context).all
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n")
            writer.write("<map>\n")
            for ((key, value) in all) {
                val name = escapeXml(key)
                when (value) {
                    is String -> writer.write("    <string name=\"$name\">${escapeXml(value)}</string>\n")
                    is Int -> writer.write("    <int name=\"$name\" value=\"$value\" />\n")
                    is Boolean -> writer.write("    <boolean name=\"$name\" value=\"$value\" />\n")
                    is Long -> writer.write("    <long name=\"$name\" value=\"$value\" />\n")
                    is Float -> writer.write("    <float name=\"$name\" value=\"$value\" />\n")
                    is Set<*> -> {
                        writer.write("    <set name=\"$name\">\n")
                        value.filterIsInstance<String>().forEach { item ->
                            writer.write("        <string>${escapeXml(item)}</string>\n")
                        }
                        writer.write("    </set>\n")
                    }
                }
            }
            writer.write("</map>\n")
        }
    }

    fun importConfig(context: Context, inputStream: InputStream, overwrite: Boolean) {
        val imported = parseConfigXml(inputStream)
        val editor = prefs(context).edit()
        if (overwrite) editor.clear()
        for ((key, value) in imported) {
            putValue(editor, key, value)
        }
        editor.apply()
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    private fun escapeXml(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '\'' -> append("&apos;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    private fun parseConfigXml(inputStream: InputStream): HashMap<String, Any?> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        val map = HashMap<String, Any?>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.getAttributeValue(null, "name")
                when (parser.name) {
                    "string" -> if (name != null) map[name] = parser.nextText()
                    "int" -> if (name != null) map[name] = parser.getAttributeValue(null, "value")?.toIntOrNull()
                    "boolean" -> if (name != null) map[name] = parser.getAttributeValue(null, "value")?.toBoolean()
                    "long" -> if (name != null) map[name] = parser.getAttributeValue(null, "value")?.toLongOrNull()
                    "float" -> if (name != null) map[name] = parser.getAttributeValue(null, "value")?.toFloatOrNull()
                    "set" -> if (name != null) map[name] = readStringSet(parser)
                }
            }
            event = parser.next()
        }
        return map
    }

    private fun readStringSet(parser: XmlPullParser): Set<String> {
        val set = LinkedHashSet<String>()
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == "set")) {
            if (event == XmlPullParser.START_TAG && parser.name == "string") {
                set.add(parser.nextText())
            }
            event = parser.next()
        }
        return set
    }

}
