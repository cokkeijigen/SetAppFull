package ss.colytitse.setappfull

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch
import ss.colytitse.setappfull.ui.LocalDynamicColor
import ss.colytitse.setappfull.ui.SetAppFullTheme
import ss.colytitse.setappfull.ui.appSwitchColors

class MainActivity : ComponentActivity() {

    private lateinit var appInfoManager: AppInfoManager
    private var lastLanguageKey: String? = null
    // 语言变化后自增，通知界面重算应用名
    val languageRevision = mutableIntStateOf(0)
    // 主题模式（Activity 级 state，供 onResume 刷新）
    val themeModeState = mutableStateOf(AppSettings.THEME_SYSTEM_MONET)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrapLocale(newBase))
    }

    override fun onResume() {
        super.onResume()
        // 归一化比较，避免无实际变化的切换触发重建闪烁
        val currentKey = AppLanguages.effectiveLanguageKey(AppSettings.getLanguage(this), this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+：设置 applicationLocales 后由 onConfigurationChanged 重新扫描
            AppSettings.applyLanguage(this)
        } else if (lastLanguageKey != null && lastLanguageKey != currentKey) {
            // API 33 以下：语言有变则重建
            lastLanguageKey = currentKey
            recreate()
            return
        }
        lastLanguageKey = currentKey
        themeModeState.value = AppSettings.getTheme(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 语言生效后重新扫描应用名
        appInfoManager.update()
        languageRevision.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appInfoManager = AppInfoManager(this)

        // 首次启动：先进入设置页（设置页会额外显示 hello_world_1 提示并开启作用域模式）
        if (AppSettings.getHelloWorld(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        themeModeState.value = AppSettings.getTheme(this)
        setContent {
            val themeMode by themeModeState
            SetAppFullTheme(themeMode = themeMode) {
                MainScreen(
                    activity = this,
                    appInfoManager = appInfoManager,
                    refreshTheme = { themeModeState.value = AppSettings.getTheme(this) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    activity: MainActivity,
    appInfoManager: AppInfoManager,
    refreshTheme: () -> Unit = {},
) {

    var revision by remember { mutableIntStateOf(0) }       // 行内开关状态刷新（不触发重排）
    var listRevision by remember { mutableIntStateOf(0) }   // 列表数据 / 排序刷新
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // 从设置页返回后总是刷新（可能改了作用域模式、导入配置或勾选状态）
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        listRevision++
        revision++
        refreshTheme()
    }

    // 页序：0 = 用户应用（第一位），1 = 系统应用
    val PAGE_USER = 0
    val PAGE_SYSTEM = 1
    val pagerState = rememberPagerState(
        initialPage = if (AppSettings.getSwitchListType(activity) == AppSettings.USER_VIEW) {
            PAGE_USER
        } else {
            PAGE_SYSTEM
        },
        pageCount = { 2 },
    )
    val listType = pagerState.currentPage

    // 页面切换后持久化视图类型并刷新排序
    LaunchedEffect(listType) {
        AppSettings.saveSwitchList(
            activity,
            if (listType == PAGE_USER) AppSettings.USER_VIEW else AppSettings.SYSTEM_VIEW,
        )
        listRevision++
    }

    // 服务（远程配置）可用时重新读取配置。
    DisposableEffect(Unit) {
        val listener = object : App.ServiceStateListener {
            override fun onServiceStateChanged(service: XposedService?) {
                listRevision++
                revision++
            }
        }
        App.addServiceStateListener(listener, false)
        onDispose { App.removeServiceStateListener(listener) }
    }

    val scopeMode = AppSettings.getScopeMode(activity)

    // 按搜索词过滤，已设置模式的应用排前面（不依赖 revision，行内开关不触发重排）
    fun sortApps(list: List<AppItem>): List<AppItem> = list.sortedBy {
        if (AppSettings.getSetMode(activity, it.packageName) == AppSettings.NO_SET) 1 else 0
    }

    // 语言变化后让列表重算
    val langRev = activity.languageRevision.value

    val systemList = remember(searchQuery, listRevision, langRev) {
        sortApps(appInfoManager.filter(appInfoManager.systemAppList(), searchQuery))
    }
    val userList = remember(searchQuery, listRevision, langRev) {
        sortApps(appInfoManager.filter(appInfoManager.userAppList(), searchQuery))
    }

    val systemListState = rememberLazyListState()
    val userListState = rememberLazyListState()

    val listTypeLabel = stringResource(
        if (listType == PAGE_USER) R.string.list_user else R.string.list_system
    )

    fun toggleMode(item: AppItem) {
        val current = AppSettings.getSetMode(activity, item.packageName)
        val checked = current != AppSettings.NO_SET
        val next = when {
            !checked && !scopeMode -> AppSettings.MODE_1
            current == AppSettings.MODE_1 || (scopeMode && !checked) -> AppSettings.MODE_2
            else -> AppSettings.NO_SET
        }
        when (next) {
            AppSettings.MODE_1 -> AppSettings.saveAppMode(activity, item.packageName)
            AppSettings.MODE_2 -> AppSettings.saveSystemMode(activity, item.packageName)
            else -> AppSettings.deleteSelection(activity, item.packageName)
        }
        revision++
    }

    fun refresh() {
        refreshing = true
        appInfoManager.update()
        listRevision++
        revision++
        refreshing = false
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.clear_all_title)) },
            text = { Text(stringResource(R.string.clear_all_message, listTypeLabel)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val packages = if (listType == PAGE_USER) {
                            appInfoManager.userAppList().map { it.packageName }.toSet()
                        } else {
                            appInfoManager.systemAppList().map { it.packageName }.toSet()
                        }
                        AppSettings.clearSelections(activity, packages)
                        listRevision++
                        revision++
                        showClearConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.confirm_text))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel_text))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            MainTopBar(
                searchVisible = searchVisible,
                listTypeLabel = listTypeLabel,
                onRefresh = { refresh() },
                onClearAll = { showClearConfirm = true },
                onToggleSearch = {
                    if (searchVisible) {
                        searchVisible = false
                        searchQuery = ""
                    } else {
                        searchVisible = true
                    }
                },
                onSwapList = {
                    scope.launch {
                        pagerState.animateScrollToPage(1 - pagerState.currentPage)
                    }
                },
                onOpenSettings = {
                    settingsLauncher.launch(Intent(activity, SettingsActivity::class.java))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedVisibility(
                visible = searchVisible,
                enter = expandVertically(tween(durationMillis = 220)) + fadeIn(tween(durationMillis = 220)),
                exit = shrinkVertically(tween(durationMillis = 200)) + fadeOut(tween(durationMillis = 180)),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_text)) },
                    singleLine = true,
                )
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val list = if (page == PAGE_USER) userList else systemList
                    val listState = if (page == PAGE_USER) userListState else systemListState
                    // 数据变化后回到顶部（在此执行而非 refresh()，避免 item key 锚定拉回旧位置）
                    LaunchedEffect(listRevision) {
                        if (list.isNotEmpty()) {
                            listState.scrollToItem(0)
                        }
                    }
                    if (list.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "No apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 15.dp,
                                end = 15.dp,
                                top = 10.dp,
                                bottom = 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(list, key = { it.packageName }) { item ->
                                val mode = remember(revision) { AppSettings.getSetMode(activity, item.packageName) }
                                AppRow(
                                    item = item,
                                    mode = mode,
                                    scopeMode = scopeMode,
                                    onClick = { toggleMode(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MainTopBar(
    searchVisible: Boolean,
    listTypeLabel: String,
    onRefresh: () -> Unit,
    onClearAll: () -> Unit,
    onToggleSearch: () -> Unit,
    onSwapList: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.app_name2),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable { onRefresh() },
                )
                Text(
                    text = "${stringResource(R.string.version_text)}：${BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Row {
                    IconButton(onClick = { onClearAll() }) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = stringResource(R.string.clear_all),
                        )
                    }
                    IconButton(onClick = { onToggleSearch() }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(R.string.search_text),
                        )
                    }
                    IconButton(onClick = { onSwapList() }) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = listTypeLabel,
                        )
                    }
                    IconButton(onClick = { onOpenSettings() }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_text),
                        )
                    }
                }
                Text(
                    text = "($listTypeLabel)",
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .offset(y = (-5).dp)
                )
            }
        }
    }
}

@Composable
internal fun AppRow(
    item: AppItem,
    mode: Int,
    scopeMode: Boolean,
    onClick: () -> Unit,
) {
    val dynamic = LocalDynamicColor.current
    val background = when (mode) {
        AppSettings.MODE_1, AppSettings.MODE_2 ->
            if (dynamic) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)
        else ->
            if (dynamic) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)
            else MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp)
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = item.icon?.toImageBitmap()
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = item.appName,
                    modifier = Modifier
                        .size(60.dp)
                        .padding(5.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Spacer(modifier = Modifier.width(60.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 5.dp),
            ) {
                Text(
                    text = item.appName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.versionText(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.packageName,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                modifier = Modifier.width(84.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Switch(
                    checked = mode != AppSettings.NO_SET,
                    onCheckedChange = { onClick() },
                    colors = appSwitchColors(),
                )
                if (!scopeMode) {
                    val label = when (mode) {
                        AppSettings.MODE_1 -> stringResource(R.string.mode_1)
                        AppSettings.MODE_2 -> stringResource(R.string.mode_2)
                        else -> ""
                    }
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF84D4D),
                    )
                }
            }
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Preview(showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun AppListPreview() {
    val context = LocalContext.current
    val demoIcon = remember { context.getDrawable(R.mipmap.ic_launcher_round) }
    val fakeApps = listOf(
        AppItem("com.example.camera", "Camera", "14.0.1", 14000100L, demoIcon),
        AppItem("com.example.browser", "Browser", "10.2.3", 10020300L, demoIcon),
        AppItem("com.example.music", "Music", "8.4.0", 8040000L, demoIcon),
        AppItem("com.example.gallery", "Gallery", "9.1.0", 9010000L, demoIcon),
    )
    val previewModes = mapOf(
        "com.example.camera" to AppSettings.NO_SET,
        "com.example.browser" to AppSettings.MODE_1,
        "com.example.music" to AppSettings.MODE_2,
        "com.example.gallery" to AppSettings.NO_SET,
    )
    SetAppFullTheme {
        Scaffold(
            topBar = {
                MainTopBar(
                    searchVisible = false,
                    listTypeLabel = stringResource(R.string.list_user),
                    onRefresh = {},
                    onClearAll = {},
                    onToggleSearch = {},
                    onSwapList = {},
                    onOpenSettings = {},
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(fakeApps, key = { it.packageName }) { item ->
                    AppRow(
                        item = item,
                        mode = previewModes[item.packageName] ?: AppSettings.NO_SET,
                        scopeMode = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}
