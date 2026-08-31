package ss.colytitse.setappfull

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.annotation.StringRes
import java.util.Locale

/**
 * 内置语言包定义（可扩展）。
 *
 * 新增一种语言时：
 * 1. 在 res/values/ 下（或新建 values-<locale>/ 目录）补充该语言的字符串翻译。
 * 2. 在 [AppLanguages.ALL] 里按展示顺序插入一个 [AppLanguage] 条目。
 * 3. 为该语言自身的显示名补充一个字符串资源（用该语言自己的名字，例如 "English"、"中文"）。
 *
 * @property code BCP-47 语言标签（如 "en"、"zh-CN"）；[AppLanguages.SYSTEM_CODE] 表示跟随系统。
 * @property labelRes 语言自身显示名对应的字符串资源。
 */
data class AppLanguage(
    val code: String,
    @param:StringRes val labelRes: Int,
)

object AppLanguages {

    /** 「跟随系统」对应的 code。 */
    const val SYSTEM_CODE = "system"

    val SYSTEM = AppLanguage(SYSTEM_CODE, R.string.language_system)
    val ENGLISH = AppLanguage("en", R.string.language_english)
    val CHINESE = AppLanguage("zh-CN", R.string.language_chinese)

    /** 下拉列表展示顺序：第一个是「跟随系统」，其后为内置语言包。 */
    val ALL: List<AppLanguage> = listOf(SYSTEM, ENGLISH, CHINESE)

    fun fromCode(code: String?): AppLanguage =
        ALL.firstOrNull { it.code == code } ?: SYSTEM

    /**
     * 返回用于判断「实际显示语言是否相同」的归一化 key。
     *
     * 「跟随系统」解析为系统级配置的语言，繁简体通过 script 区分，
     * 因此「跟随系统(简体中文)」与「简体中文」会被视为同一种语言。
     */
    fun effectiveLanguageKey(code: String, context: Context): String {
        val locale = if (code == SYSTEM_CODE) systemLocale(context) else Locale.forLanguageTag(code)
        val language = locale.language
        if (language != "zh") return language
        // 中文：显式区分简繁体。系统 locale 可能带 script（如 zh-Hans-CN）或仅有 region（如 zh-CN），
        // 两者都表示简体；若不统一归一会导致「跟随系统(简体)」与「简体中文」被误判为不同语言而闪屏。
        val script = when {
            locale.script.isNotEmpty() -> locale.script
            locale.country in setOf("TW", "HK", "MO") -> "Hant"
            else -> "Hans"
        }
        return "zh-$script"
    }

    /**
     * 系统级语言（不受 app 的 applicationLocales 影响）。
     * API 33+ 用 LocaleManager.getSystemLocales()（明确忽略 app-specific override）；
     * 低版本用 Resources.getSystem()。
     */
    fun systemLocale(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            val system = manager?.systemLocales
            if (system != null && !system.isEmpty) return system[0]
        }
        return Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()
    }
}
