package ss.colytitse.setappfull

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap

data class AppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable?
) {
    fun versionText(): String = "$versionName ($versionCode)"
}

class AppInfoManager(context: Context) {

    private val packageManager = context.packageManager

    private var systemApps: List<AppItem> = emptyList()
    private var userApps: List<AppItem> = emptyList()

    init {
        update()
    }

    fun update() {
        val all = packageManager.getInstalledPackages(0)
        val system = ArrayList<AppItem>()
        val user = ArrayList<AppItem>()
        for (info in all) {
            val ai = info.applicationInfo ?: continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val item = AppItem(
                packageName = info.packageName,
                appName = ai.loadLabel(packageManager).toString(),
                versionName = info.versionName ?: "",
                versionCode = info.longVersionCode,
                icon = ai.loadIcon(packageManager)
            )
            if (isSystem) system.add(item) else user.add(item)
        }
        systemApps = system
        userApps = user
    }

    fun systemAppList(): List<AppItem> = systemApps

    fun userAppList(): List<AppItem> = userApps

    fun filter(list: List<AppItem>, query: String): List<AppItem> {
        if (query.isEmpty()) return list
        return list.filter {
            it.packageName.contains(query, ignoreCase = true) ||
                it.appName.contains(query, ignoreCase = true)
        }
    }
}

/** Converts a Drawable to a Compose ImageBitmap without requiring androidx.core-ktx. */
fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable) {
        val bmp = bitmap
        if (bmp != null) return bmp.asImageBitmap()
    }
    val width = if (intrinsicWidth > 0) intrinsicWidth else 1
    val height = if (intrinsicHeight > 0) intrinsicHeight else 1
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
