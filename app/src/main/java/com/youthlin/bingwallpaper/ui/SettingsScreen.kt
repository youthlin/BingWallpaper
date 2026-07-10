package com.youthlin.bingwallpaper.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.youthlin.bingwallpaper.R
import com.youthlin.bingwallpaper.data.SettingsStore
import com.youthlin.bingwallpaper.data.UserSettings
import com.youthlin.bingwallpaper.data.WallpaperTarget
import com.youthlin.bingwallpaper.work.WorkScheduler
import kotlinx.coroutines.launch

/**
 * 设置页面。
 * 包含：每日自动更换开关、时间选择、立即执行、壁纸目标、Wi-Fi Only、保存到图库、Wi-Fi 预取、关于。
 * 设置变更会通过 DataStore 持久化，并通过 LaunchedEffect 同步更新 WorkManager 调度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val settings by store.flow.collectAsState(initial = UserSettings())
    var showTargetDialog by remember { mutableStateOf(false) }
    var showMarketDialog by remember { mutableStateOf(false) }
    var showGalleryDialog by remember { mutableStateOf(false) }
    var showFaq by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var pendingGallerySelection by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingGallerySelection?.let { (saveUhd, savePortrait) ->
                scope.launch { store.setGallerySelection(saveUhd, savePortrait) }
                pendingGallerySelection = null
            }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.msg_storage_permission_required),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 设置变更时自动同步 WorkManager 调度
    LaunchedEffect(settings) { WorkScheduler.apply(context, settings) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
                windowInsets = WindowInsets(0),
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 每日自动更换开关
            SwitchRow(
                title = stringResource(R.string.setting_auto_daily),
                summary = stringResource(R.string.setting_auto_daily_summary),
                checked = settings.autoDaily,
                onChange = { scope.launch { store.setAutoDaily(it) } }
            )
            HorizontalDivider()
            // 时间选择（仅自动更换开启时可用）
            ClickableRow(
                title = stringResource(R.string.setting_time),
                summary = "%02d:%02d".format(settings.hour, settings.minute),
                enabled = settings.autoDaily
            ) {
                TimePickerDialog(
                    context,
                    { _, h, m -> scope.launch { store.setTime(h, m) } },
                    settings.hour, settings.minute, true
                ).show()
            }
            HorizontalDivider()
            // 必应市场（影响每日图片和文案区域）
            ClickableRow(
                title = stringResource(R.string.setting_market),
                summary = settings.market
            ) { showMarketDialog = true }
            HorizontalDivider()
            // 立即执行一次
            ClickableRow(
                title = stringResource(R.string.setting_run_now),
                summary = null
            ) {
                if (settings.saveToGallery && needsLegacyStoragePermission(context)) {
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    return@ClickableRow
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.msg_applying),
                    Toast.LENGTH_SHORT
                ).show()
                WorkScheduler.runOnce(context, requireUnmetered = settings.onlyWifi)
            }
            HorizontalDivider()
            // 壁纸目标（主屏/锁屏/双屏）
            ClickableRow(
                title = stringResource(R.string.setting_target),
                summary = stringResource(
                    when (settings.target) {
                        WallpaperTarget.HOME -> R.string.target_home
                        WallpaperTarget.LOCK -> R.string.target_lock
                        WallpaperTarget.BOTH -> R.string.target_both
                    }
                )
            ) { showTargetDialog = true }
            HorizontalDivider()
            // 仅 Wi-Fi 下更新
            SwitchRow(
                title = stringResource(R.string.setting_only_wifi),
                summary = stringResource(R.string.setting_only_wifi_summary),
                checked = settings.onlyWifi,
                onChange = { scope.launch { store.setOnlyWifi(it) } }
            )
            HorizontalDivider()
            // 自动保存到系统图库
            ClickableRow(
                title = stringResource(R.string.setting_save_to_gallery),
                summary = gallerySummary(settings)
            ) { showGalleryDialog = true }
            HorizontalDivider()
            // Wi-Fi 下自动预下载大图
            SwitchRow(
                title = stringResource(R.string.setting_prefetch_wifi),
                summary = stringResource(R.string.setting_prefetch_wifi_summary),
                checked = settings.prefetchOnWifi,
                onChange = { scope.launch { store.setPrefetchOnWifi(it) } }
            )
            HorizontalDivider()
            // 常见问题
            ClickableRow(
                title = stringResource(R.string.setting_faq),
                summary = stringResource(R.string.setting_faq_summary)
            ) { showFaq = true }
            HorizontalDivider()
            // 关于
            ClickableRow(
                title = stringResource(R.string.setting_about),
                summary = stringResource(R.string.setting_about_summary)
            ) { showAbout = true }
        }
    }

    // 壁纸目标选择弹窗
    if (showTargetDialog) {
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            confirmButton = {
                TextButton(onClick = { showTargetDialog = false }) { Text("OK") }
            },
            title = { Text(stringResource(R.string.setting_target)) },
            text = {
                Column {
                    listOf(
                        WallpaperTarget.HOME to R.string.target_home,
                        WallpaperTarget.LOCK to R.string.target_lock,
                        WallpaperTarget.BOTH to R.string.target_both
                    ).forEach { (target, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { scope.launch { store.setTarget(target) } }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = settings.target == target,
                                onClick = { scope.launch { store.setTarget(target) } }
                            )
                            Text(stringResource(label))
                        }
                    }
                }
            }
        )
    }

    // 图库保存设置弹窗
    if (showGalleryDialog) {
        var saveUhd by remember(settings.saveToGallery, settings.saveUhdToGallery) {
            mutableStateOf(settings.saveToGallery && settings.saveUhdToGallery)
        }
        var savePortrait by remember(settings.saveToGallery, settings.savePortraitToGallery) {
            mutableStateOf(settings.saveToGallery && settings.savePortraitToGallery)
        }
        AlertDialog(
            onDismissRequest = { showGalleryDialog = false },
            title = { Text(stringResource(R.string.setting_save_to_gallery)) },
            text = {
                Column {
                    CheckboxRow(
                        title = stringResource(R.string.setting_save_uhd_to_gallery),
                        summary = stringResource(R.string.setting_save_uhd_to_gallery_summary),
                        checked = saveUhd,
                        onChange = { saveUhd = it }
                    )
                    CheckboxRow(
                        title = stringResource(R.string.setting_save_portrait_to_gallery),
                        summary = stringResource(R.string.setting_save_portrait_to_gallery_summary),
                        checked = savePortrait,
                        onChange = { savePortrait = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if ((saveUhd || savePortrait) && needsLegacyStoragePermission(context)) {
                        pendingGallerySelection = saveUhd to savePortrait
                        storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        scope.launch { store.setGallerySelection(saveUhd, savePortrait) }
                    }
                    showGalleryDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGalleryDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Bing 市场设置弹窗
    if (showMarketDialog) {
        var marketInput by remember(settings.market) { mutableStateOf(settings.market) }
        AlertDialog(
            onDismissRequest = { showMarketDialog = false },
            title = { Text(stringResource(R.string.setting_market)) },
            text = {
                Column {
                    Text(
                        text = stringResource(
                            R.string.setting_market_summary,
                            SettingsStore.defaultMarket()
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = marketInput,
                        onValueChange = { marketInput = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.setting_market_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { store.setMarket(marketInput) }
                    showMarketDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch { store.resetMarketToDevice() }
                        showMarketDialog = false
                    }) {
                        Text(stringResource(R.string.action_reset_device_market))
                    }
                    TextButton(onClick = { showMarketDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        )
    }

    // 常见问题弹窗
    if (showFaq) {
        AlertDialog(
            onDismissRequest = { showFaq = false },
            title = { Text(stringResource(R.string.faq_auto_change_title)) },
            text = { Text(stringResource(R.string.faq_auto_change_body)) },
            confirmButton = {
                TextButton(onClick = { showFaq = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    // 关于弹窗
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.about_body))
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { openUrl(context, "https://github.com/youthlin/bingwallpaper") }) {
                        Text("GitHub")
                    }
                    TextButton(onClick = { openUrl(context, "https://youthlin.com/?p=1172") }) {
                        Text("Blog")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

/** 在浏览器中打开 URL */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun needsLegacyStoragePermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
}

private suspend fun SettingsStore.setGallerySelection(saveUhd: Boolean, savePortrait: Boolean) {
    setSaveUhdToGallery(saveUhd)
    setSavePortraitToGallery(savePortrait)
    setSaveToGallery(saveUhd || savePortrait)
}

@Composable
private fun gallerySummary(settings: UserSettings): String {
    if (!settings.saveToGallery || (!settings.saveUhdToGallery && !settings.savePortraitToGallery)) {
        return stringResource(R.string.setting_save_gallery_off)
    }
    return when {
        settings.saveUhdToGallery && settings.savePortraitToGallery ->
            stringResource(R.string.setting_save_gallery_both)
        settings.saveUhdToGallery -> stringResource(R.string.setting_save_gallery_uhd)
        else -> stringResource(R.string.setting_save_gallery_portrait)
    }
}

@Composable
private fun CheckboxRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 开关行组件（标题 + 说明 + 开关） */
@Composable
private fun SwitchRow(
    title: String, summary: String?,
    checked: Boolean, onChange: (Boolean) -> Unit
) {
    val toggle = { onChange(!checked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = toggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = { toggle() })
    }
}

/** 可点击行组件（标题 + 说明，点击触发操作） */
@Composable
private fun ClickableRow(
    title: String, summary: String?,
    enabled: Boolean = true, onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            title, style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall)
    }
}
