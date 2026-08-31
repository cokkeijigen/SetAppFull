package ss.colytitse.setappfull

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ss.colytitse.setappfull.ui.SetAppFullTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SetAppFullTheme {
                SettingsScreen(context = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(context: Context) {

    var scopeMode by remember { mutableStateOf(AppSettings.getScopeMode(context)) }
    var showFirstRunHint by remember { mutableStateOf(AppSettings.getHelloWorld(context)) }
    var showIcon by remember { mutableStateOf(AppSettings.getShowLauncherIcon(context)) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var moduleActive by remember { mutableStateOf(App.getService() != null) }

    DisposableEffect(Unit) {
        val listener = object : App.ServiceStateListener {
            override fun onServiceStateChanged(service: XposedService?) {
                moduleActive = service != null
            }
        }
        App.addServiceStateListener(listener, notifyImmediately = true)
        onDispose {
            App.removeServiceStateListener(listener)
        }
    }

    val scope = rememberCoroutineScope()

    fun doExport(uri: Uri) {
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: error("openOutputStream returned null")
                    out.use { AppSettings.exportConfig(context, it) }
                }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) context.getString(R.string.export_success)
                else context.getString(R.string.export_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun doImport(uri: Uri, overwrite: Boolean) {
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("openInputStream returned null")
                    input.use { AppSettings.importConfig(context, it, overwrite) }
                }
            }.isSuccess
            if (ok) (context as? Activity)?.setResult(Activity.RESULT_OK)
            Toast.makeText(
                context,
                if (ok) context.getString(R.string.import_success)
                else context.getString(R.string.import_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // 预览（inspection）环境没有 ActivityResultRegistryOwner，创建 launcher 会抛异常，
    // 因此预览时用 null 占位，点击时通过 ?. 安全忽略；运行时正常创建。
    val inspection = LocalInspectionMode.current
    val exportLauncher = if (inspection) {
        null
    } else {
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/xml"),
        ) { uri ->
            if (uri != null) doExport(uri)
        }
    }

    val importLauncher = if (inspection) {
        null
    } else {
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) importUri = uri
        }
    }

    // Replicate the original first-run behaviour: append the extra hint and enable scope mode.
    LaunchedEffect(Unit) {
        if (showFirstRunHint) {
            AppSettings.setHelloWorld(context)
            AppSettings.setScopeMode(context, true)
            scopeMode = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (showFirstRunHint) R.string.hello_title
                            else R.string.settings_text
                        ),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Text(
                        text = stringResource(
                            if (moduleActive) R.string.module_active
                            else R.string.module_inactive
                        ),
                        fontWeight = if (moduleActive) FontWeight.Medium else FontWeight.Bold,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Welcome / first-run warning
            SectionCard(
                header = stringResource(R.string.hello),
                body = if (showFirstRunHint) {
                    stringResource(R.string.hello_world) + "\n\n" + stringResource(R.string.hello_world_1)
                } else {
                    stringResource(R.string.hello_world)
                },
            )

            // Export config（首次启动时隐藏）
            if (!showFirstRunHint) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable {
                            val name = "setappfull_config_" +
                                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xml"
                            exportLauncher?.launch(name)
                        },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.export_config),
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.Upload,
                            contentDescription = stringResource(R.string.export_config),
                        )
                    }
                }
            }

            // Import config
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable {
                        importLauncher?.launch(arrayOf("text/xml", "application/xml", "*/*"))
                    },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.import_config),
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.FileDownload,
                        contentDescription = stringResource(R.string.import_config),
                    )
                }
            }

            // 显示桌面图标
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.show_launcher_icon),
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = showIcon,
                        onCheckedChange = {
                            showIcon = it
                            AppSettings.setShowLauncherIcon(context, it)
                        },
                    )
                }
            }

            // Scope mode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.scope_mode_text),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = scopeMode,
                            onCheckedChange = {
                                scopeMode = it
                                AppSettings.setScopeMode(context, it)
                            },
                        )
                    }
                    Text(
                        text = stringResource(R.string.scope_mode_description),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(
                header = stringResource(R.string.mode_1),
                body = stringResource(R.string.mode_explain_1),
            )

            SectionCard(
                header = stringResource(R.string.mode_2),
                body = stringResource(R.string.mode_explain_2),
            )

            SectionCard(
                header = stringResource(R.string.description_title),
                body = stringResource(R.string.description_text),
            )

            // About
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.about_text),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                    Text(
                        text = "- ${stringResource(R.string.app_name)} -",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                    )
                    Text(
                        text = stringResource(R.string.xposeddescription),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                    )
                    Image(
                        painter = painterResource(R.drawable.kyaru),
                        contentDescription = stringResource(R.string.about_text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(10.dp)
                            .clickable {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "https://github.com/cokkeijigen/setAppFull".toUri(),
                                    )
                                )
                            },
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        text = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) | by iTsukezigen",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text(stringResource(R.string.import_dialog_title)) },
            text = { Text(stringResource(R.string.import_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        importUri = null
                        doImport(uri, overwrite = true)
                    },
                ) { Text(stringResource(R.string.import_overwrite)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        importUri = null
                        doImport(uri, overwrite = false)
                    },
                ) { Text(stringResource(R.string.import_merge)) }
            },
        )
    }
}

@Composable
internal fun SectionCard(header: String, body: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            Text(
                text = header,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
            Text(
                text = body,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 500, heightDp = 1500)
@Composable
private fun SettingsScreenPreview() {
    SetAppFullTheme {
        SettingsScreen(context = LocalContext.current)
    }
}
