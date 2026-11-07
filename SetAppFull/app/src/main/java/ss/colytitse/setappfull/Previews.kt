package ss.colytitse.setappfull

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ss.colytitse.setappfull.ui.SetAppFullTheme

// ---------------------------------------------------------------------------------- sample data

private fun sampleApp(
    packageName: String,
    appName: String,
    versionName: String,
    versionCode: Long,
): AppItem = AppItem(
    packageName = packageName,
    appName = appName,
    versionName = versionName,
    versionCode = versionCode,
    icon = null,
)

// ---------------------------------------------------------------------------------- AppRow previews

@Preview(name = "AppRow — 未设置", showBackground = true)
@Composable
private fun AppRowPreviewNone() {
    SetAppFullTheme {
        PreviewColumn {
            AppRow(
                item = sampleApp("com.example.demo", "Demo App", "1.0.0", 100L),
                mode = AppSettings.NO_SET,
                scopeMode = false,
                onClick = {},
            )
        }
    }
}

@Preview(name = "AppRow — 模式一", showBackground = true)
@Composable
private fun AppRowPreviewMode1() {
    SetAppFullTheme {
        PreviewColumn {
            AppRow(
                item = sampleApp("com.example.demo", "Demo App", "1.0.0", 100L),
                mode = AppSettings.MODE_1,
                scopeMode = false,
                onClick = {},
            )
        }
    }
}

@Preview(name = "AppRow — 模式二", showBackground = true)
@Composable
private fun AppRowPreviewMode2() {
    SetAppFullTheme {
        PreviewColumn {
            AppRow(
                item = sampleApp("com.example.demo", "Demo App", "1.0.0", 100L),
                mode = AppSettings.MODE_2,
                scopeMode = false,
                onClick = {},
            )
        }
    }
}

@Preview(name = "AppRow — 作用域模式", showBackground = true)
@Composable
private fun AppRowPreviewScopeMode() {
    SetAppFullTheme {
        PreviewColumn {
            AppRow(
                item = sampleApp("com.example.demo", "Demo App", "1.0.0", 100L),
                mode = AppSettings.MODE_1,
                scopeMode = true,
                onClick = {},
            )
        }
    }
}

@Preview(name = "AppRow — 列表", showBackground = true, widthDp = 400)
@Composable
private fun AppRowListPreview() {
    SetAppFullTheme {
        PreviewColumn {
            AppRow(
                item = sampleApp("com.example.alpha", "Alpha", "2.1.0", 210L),
                mode = AppSettings.NO_SET,
                scopeMode = false,
                onClick = {},
            )
            AppRow(
                item = sampleApp("com.example.beta", "Beta", "3.0.1", 301L),
                mode = AppSettings.MODE_1,
                scopeMode = false,
                onClick = {},
            )
            AppRow(
                item = sampleApp("com.example.gamma", "Gamma", "0.9.9", 99L),
                mode = AppSettings.MODE_2,
                scopeMode = false,
                onClick = {},
            )
        }
    }
}

@Preview(name = "AppRow — 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppRowPreviewDark() {
    SetAppFullTheme {
        PreviewColumn {
            AppRow(
                item = sampleApp("com.example.demo", "Demo App", "1.0.0", 100L),
                mode = AppSettings.MODE_1,
                scopeMode = false,
                onClick = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------------- SectionCard previews

@Preview(name = "SectionCard", showBackground = true)
@Composable
private fun SectionCardPreview() {
    SetAppFullTheme {
        PreviewColumn {
            SectionCard(
                header = "模式一",
                body = "这是模式一的说明文字，用来预览设置页卡片样式。",
            )
        }
    }
}

// ---------------------------------------------------------------------------------- helpers

@Composable
private fun PreviewColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}
