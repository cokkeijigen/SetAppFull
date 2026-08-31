package ss.colytitse.setappfull;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.github.libxposed.api.XposedModule;

/**
 * Module entry point (hook side, Java).
 * Uses the modern libxposed 102 API instead of the legacy de.robv.android.xposed API.
 * The module app's configuration is read through Remote Preferences (group "config"),
 * which replaces the legacy XSharedPreferences.
 */
@SuppressWarnings({"unused", "deprecation"})
@SuppressLint({"InternalInsetResource", "DiscouragedApi", "PrivateApi"})
public class MainHook extends XposedModule {

    private static final String TAG = "MainHook";

    /** Status bar inset mask in androidx.core. Always bit 0 (WindowInsetsCompat.Type.FIRST = 1). */
    private static final int STATUS_BARS_MASK = 1;

    private List<String> systemModeList = Collections.emptyList();
    private List<String> appModeList = Collections.emptyList();
    private volatile int statusBarHeightId = 0;
    private volatile boolean isScopeMode = true;
    private volatile long lastUpdateTime = 0;

    private ScheduledExecutorService scheduledExecutorService;
    private List<WindowManager.LayoutParams> layoutParamsList;

    // --------------------------------------------------------------------------------- config

    private void update() {
        SharedPreferences prefs = getRemotePreferences(AppSettings.CONFIG_NAME);
        this.systemModeList = readStringList(prefs, "SystemMode");
        this.appModeList = readStringList(prefs, "AppMode");
        this.isScopeMode = prefs.getBoolean("scope_mode_switch", true);
        this.lastUpdateTime = System.currentTimeMillis();
    }

    private static List<String> readStringList(SharedPreferences prefs, String key) {
        Set<String> set;
        try {
            set = prefs.getStringSet(key, Collections.emptySet());
        } catch (ClassCastException e) {
            // 旧版本存的是 #pkg# 字符串，忽略（重新勾选即可）
            set = Collections.emptySet();
        }
        return set == null ? Collections.emptyList() : new ArrayList<>(set);
    }

    // --------------------------------------------------------------------------------- lifecycle

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        try {
            update();
            hookSystemMode(param.getClassLoader());
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook system mode", t);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (BuildConfig.APPLICATION_ID.equals(packageName)) {
            return;
        }
        if (!param.isFirstPackage()) {
            return;
        }
        try {
            update();
        } catch (Throwable ignored) {
        }
        if (isScopeMode || appModeList.contains(packageName)) {
            try {
                hookAppMode(param.getClassLoader());
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Failed to hook app mode for " + packageName, t);
            }
        }
    }

    // --------------------------------------------------------------------------------- system mode

    private void hookSystemMode(ClassLoader classLoader) throws Throwable {
        Class<?> displayPolicy = classLoader.loadClass("com.android.server.wm.DisplayPolicy");
        Method layoutWindowLw = findMethod(displayPolicy, "layoutWindowLw", 3);
        if (layoutWindowLw == null) {
            log(Log.WARN, TAG, "DisplayPolicy.layoutWindowLw not found");
            return;
        }
        hook(layoutWindowLw).intercept(chain -> {
            Object[] args = chain.getArgs().toArray();
            WindowManager.LayoutParams attrs =
                    (WindowManager.LayoutParams) getObjectField(args[0], "mAttrs");
            if (attrs != null) {
                if (attrs.type > WindowManager.LayoutParams.LAST_APPLICATION_WINDOW) {
                    return chain.proceed();
                }
                if (BuildConfig.APPLICATION_ID.equals(attrs.packageName)) {
                    if (System.currentTimeMillis() - lastUpdateTime >= 35) {
                        update();
                    }
                } else if (systemModeList.contains(attrs.packageName)) {
                    attrs.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                }
            }
            return chain.proceed();
        });
    }

    // --------------------------------------------------------------------------------- app mode

    private void hookAppMode(ClassLoader classLoader) throws Throwable {
        // android.view.View.setSystemUiVisibility(int)
        try {
            Method m = findMethod(classLoader.loadClass("android.view.View"), "setSystemUiVisibility", 1);
            if (m != null) {
                hook(m).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    args[0] = fullscreenUiFlags();
                    return chain.proceed(args);
                });
            }
        } catch (Throwable ignored) {
        }

        // android.view.Window.setFlags(int, int)
        try {
            Method m = findMethod(classLoader.loadClass("android.view.Window"), "setFlags", 2);
            if (m != null) {
                hook(m).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    args[0] = WindowManager.LayoutParams.FLAG_FULLSCREEN;
                    args[1] = WindowManager.LayoutParams.FLAG_FULLSCREEN;
                    return chain.proceed(args);
                });
            }
        } catch (Throwable ignored) {
        }

        // android.view.Window.addFlags(int)
        try {
            Method m = findMethod(classLoader.loadClass("android.view.Window"), "addFlags", 1);
            if (m != null) {
                hook(m).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    args[0] = WindowManager.LayoutParams.FLAG_FULLSCREEN;
                    return chain.proceed(args);
                });
            }
        } catch (Throwable ignored) {
        }

        // android.view.Window.setAttributes(LayoutParams)
        try {
            Method m = findMethod(classLoader.loadClass("android.view.Window"), "setAttributes", 1);
            if (m != null) {
                hook(m).intercept(chain -> {
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) chain.getArg(0);
                    if (lp != null) {
                        lp.layoutInDisplayCutoutMode =
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable ignored) {
        }

        // android.view.Window.getAttributes()
        try {
            Method m = findMethod(classLoader.loadClass("android.view.Window"), "getAttributes", 0);
            if (m != null) {
                hook(m).intercept(chain -> {
                    Object result = chain.proceed();
                    if (result instanceof WindowManager.LayoutParams) {
                        addLayoutParamsGuardian((WindowManager.LayoutParams) result);
                    }
                    return result;
                });
            }
        } catch (Throwable ignored) {
        }

        // android.app.Activity.onCreate(Bundle)
        try {
            Method m = findMethod(classLoader.loadClass("android.app.Activity"), "onCreate", 1);
            if (m != null) {
                hook(m).intercept(chain -> {
                    Activity activity = (Activity) chain.getThisObject();
                    if (statusBarHeightId == 0) {
                        statusBarHeightId = activity.getResources()
                                .getIdentifier("status_bar_height", "dimen", "android");
                    }
                    Object result = chain.proceed();
                    applyFullscreen(activity);
                    return result;
                });
            }
        } catch (Throwable ignored) {
        }

        // android.content.res.Resources.getDimensionPixelSize(int)
        try {
            Method m = findMethod(classLoader.loadClass("android.content.res.Resources"),
                    "getDimensionPixelSize", 1);
            if (m != null) {
                hook(m).intercept(chain -> {
                    int resourceId = (Integer) chain.getArg(0);
                    if (statusBarHeightId != 0 && resourceId == statusBarHeightId) {
                        return 0;
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable ignored) {
        }

        // androidx.core.view.WindowInsetsCompat.getDisplayCutout() (best effort)
        try {
            Method m = findMethod(classLoader.loadClass("androidx.core.view.WindowInsetsCompat"),
                    "getDisplayCutout", 0);
            if (m != null) {
                hook(m).intercept(chain -> null);
            }
        } catch (Throwable ignored) {
        }

        // androidx.core.view.WindowInsetsCompat.getInsets(int)：把 statusBars 的 top 抹成 0
        try {
            Method m = findMethod(classLoader.loadClass("androidx.core.view.WindowInsetsCompat"),
                    "getInsets", 1);
            if (m != null) {
                hook(m).intercept(chain -> {
                    Object insets = chain.proceed();
                    int typeMask = (Integer) chain.getArg(0);
                    if (insets != null && (typeMask & STATUS_BARS_MASK) != 0) {
                        setIntField(insets, "top", 0);
                    }
                    return insets;
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static int fullscreenUiFlags() {
        return View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    }

    private void applyFullscreen(Activity activity) {
        Window window = activity.getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                window.setDecorFitsSystemWindows(false);
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        window.getDecorView().setSystemUiVisibility(fullscreenUiFlags());
        WindowManager.LayoutParams lp =
                (WindowManager.LayoutParams) getObjectField(window, "mWindowAttributes");
        if (lp == null) {
            lp = window.getAttributes();
        }
        if (lp != null) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private void layoutParamsGuardian() {
        if (layoutParamsList == null || layoutParamsList.isEmpty()) {
            return;
        }
        for (WindowManager.LayoutParams lp : layoutParamsList) {
            if (lp.layoutInDisplayCutoutMode
                    == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                continue;
            }
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private void addLayoutParamsGuardian(WindowManager.LayoutParams lp) {
        if (layoutParamsList == null) {
            layoutParamsList = Collections.synchronizedList(new ArrayList<>());
        }
        if (!layoutParamsList.contains(lp)) {
            layoutParamsList.add(lp);
        }
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newScheduledThreadPool(1);
            scheduledExecutorService.scheduleWithFixedDelay(
                    this::layoutParamsGuardian, 0, 30, TimeUnit.MILLISECONDS);
        }
    }

    // --------------------------------------------------------------------------------- reflection

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        Class<?> current = clazz;
        while (current != null) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == paramCount) {
                    m.setAccessible(true);
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object getObjectField(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field f = findField(target.getClass(), name);
            if (f != null) {
                return f.get(target);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void setIntField(Object target, String name, int value) {
        if (target == null) {
            return;
        }
        try {
            Field f = findField(target.getClass(), name);
            if (f != null) {
                f.setInt(target, value);
            }
        } catch (Throwable ignored) {
        }
    }
}
