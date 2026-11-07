package ss.colytitse.setappfull

import android.app.Application
import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.Volatile

/**
 * Module UI entry. Registers itself as the libxposed service listener so the UI can read/write
 * Remote Preferences (the modern replacement for the legacy XSharedPreferences).
 */
class App : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        @Volatile
        private var mService: XposedService? = null

        private val serviceStateListeners = CopyOnWriteArraySet<ServiceStateListener>()

        fun getService(): XposedService? = mService

        fun addServiceStateListener(listener: ServiceStateListener, notifyImmediately: Boolean) {
            serviceStateListeners.add(listener)
            if (notifyImmediately) {
                dispatchServiceState(listener, mService)
            }
        }

        fun removeServiceStateListener(listener: ServiceStateListener) {
            serviceStateListeners.remove(listener)
        }

        private fun dispatchServiceState(listener: ServiceStateListener, service: XposedService?) {
            if (serviceStateListeners.contains(listener)) {
                listener.onServiceStateChanged(service)
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.wrapLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
        AppSettings.applyLauncherIcon(this)
        AppSettings.applyLanguage(this)
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
        for (listener in serviceStateListeners) {
            dispatchServiceState(listener, mService)
        }
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
        for (listener in serviceStateListeners) {
            dispatchServiceState(listener, mService)
        }
    }

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }
}
