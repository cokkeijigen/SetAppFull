package ss.colytitse.setappfull

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch
import ss.colytitse.setappfull.ui.SetAppFullTheme

class MainActivity : ComponentActivity() {

    private lateinit var appInfoManager: AppInfoManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appInfoManager = AppInfoManager(this)

        // 首次启动：先进入设置页（设置页会额外显示 hello_world_1 提示并开启作用域模式）
        if (AppSettings.getHelloWorld(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setContent {
            SetAppFullTheme {
                MainScreen(activity = this, appInfoManager = appInfoManager)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(activity: MainActivity, appInfoManager: AppInfoManager) {
    val context = activity

    var revision by remember { mutableIntStateOf(0) }       // 行内开关状态刷新（不触发重排）
    var listRevision by remember { mutableIntStateOf(0) }   // 列表数据 / 排序刷新
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // 从设置页返回后总是刷新：可能修改了作用域模式、导入了配置，或勾选状态需要重排
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        listRevision++
        revision++
    }

    // 页序：0 = 用户应用（第一位），1 = 系统应用
    val PAGE_USER = 0
    val PAGE_SYSTEM = 1
    val pagerState = rememberPagerState(
        initialPage = if (AppSettings.getSwitchListType(context) == AppSettings.USER_VIEW) {
            PAGE_USER
        } else {
            PAGE_SYSTEM
        },
        pageCount = { 2 },
    )
    val listType = pagerState.currentPage

    // 页面变化（滑动或按钮动画）后持久化当前视图类型，并刷新列表排序
    LaunchedEffect(listType) {
        AppSettings.saveSwitchList(
            context,
            if (listType == PAGE_USER) AppSettings.USER_VIEW else AppSettings.SYSTEM_VIEW,
        )
        listRevision++
    }

    // Re-read configuration when the libxposed service (remote preferences) becomes available.
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

    val scopeMode = AppSettings.getScopeMode(context)

    // 每个页面各自一份列表：按搜索词过滤，已设置模式的应用排前面。不依赖 `revision`，
    // 因此行内开关切换不会触发重新过滤/排序。
    fun sortApps(list: List<AppItem>): List<AppItem> = list.sortedBy {
        if (AppSettings.getSetMode(context, it.packageName) == AppSettings.NO_SET) 1 else 0
    }

    val systemList = remember(searchQuery, listRevision) {
        sortApps(appInfoManager.filter(appInfoManager.systemAppList(), searchQuery))
    }
    val userList = remember(searchQuery, listRevision) {
        sortApps(appInfoManager.filter(appInfoManager.userAppList(), searchQuery))
    }

    val systemListState = rememberLazyListState()
    val userListState = rememberLazyListState()

    val listTypeLabel = stringResource(
        if (listType == PAGE_USER) R.string.list_user else R.string.list_system
    )

    fun toggleMode(item: AppItem) {
        val current = AppSettings.getSetMode(context, item.packageName)
        val checked = current != AppSettings.NO_SET
        val next = when {
            !checked && !scopeMode -> AppSettings.MODE_1
            current == AppSettings.MODE_1 || (scopeMode && !checked) -> AppSettings.MODE_2
            else -> AppSettings.NO_SET
        }
        when (next) {
            AppSettings.MODE_1 -> AppSettings.saveAppMode(context, item.packageName)
            AppSettings.MODE_2 -> AppSettings.saveSystemMode(context, item.packageName)
            else -> AppSettings.deleteSelection(context, item.packageName)
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
                        AppSettings.clearSelections(context, packages)
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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.clickable { refresh() },
                        )
                        Text(
                            text = "Version：${BuildConfig.VERSION_NAME} ($listTypeLabel)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = stringResource(R.string.clear_all),
                        )
                    }
                    IconButton(onClick = {
                        if (searchVisible) {
                            searchVisible = false
                            searchQuery = ""
                        } else {
                            searchVisible = true
                        }
                    }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(R.string.search_text),
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(1 - pagerState.currentPage)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = listTypeLabel,
                        )
                    }
                    IconButton(onClick = {
                        settingsLauncher.launch(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_text),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searchVisible) {
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
                    // 列表数据变化（刷新/清空）后回到顶部。放在此处（而非 refresh()）确保
                    // 在重排后的新数据上执行，避免 LazyColumn 用 item key 锚定把位置拉回旧顶部。
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
                                val mode = remember(revision) { AppSettings.getSetMode(context, item.packageName) }
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
internal fun AppRow(
    item: AppItem,
    mode: Int,
    scopeMode: Boolean,
    onClick: () -> Unit,
) {
    val background = when (mode) {
        AppSettings.MODE_1 -> Color(0x43009AFF)
        AppSettings.MODE_2 -> Color(0x4500BFA5)
        else -> Color(0x146B8BFF)
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
