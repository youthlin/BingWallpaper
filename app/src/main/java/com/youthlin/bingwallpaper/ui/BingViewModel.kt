package com.youthlin.bingwallpaper.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.youthlin.bingwallpaper.R
import com.youthlin.bingwallpaper.data.BingApiFactory
import com.youthlin.bingwallpaper.data.DownloadProgress
import com.youthlin.bingwallpaper.data.SettingsStore
import com.youthlin.bingwallpaper.data.WallpaperImage
import com.youthlin.bingwallpaper.data.WallpaperRepository
import com.youthlin.bingwallpaper.data.db.WallpaperEntity
import com.youthlin.bingwallpaper.work.SetWallpaperWorker
import com.youthlin.bingwallpaper.work.WorkScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页 UI 状态（loading 和 setting 标志） */
data class HomeUiState(val loading: Boolean = false, val setting: Boolean = false)

/**
 * 应用主 ViewModel，管理所有 UI 状态和用户操作。
 * 生命周期跟随 Activity，使用 AndroidViewModel 以便访问 Context。
 *
 * 核心数据流：
 * repo.observeAll() → Room Flow → stateIn() → wallpapers StateFlow → UI collectAsStateWithLifecycle()
 */
class BingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WallpaperRepository(app)
    private val settings = SettingsStore(app)

    /** 壁纸列表，来自 Room 响应式查询。WhileSubscribed(5000) 表示失去订阅者 5 秒后停止上游。 */
    val wallpapers: StateFlow<List<WallpaperEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userSettings = settings.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.youthlin.bingwallpaper.data.UserSettings()
    )

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    /** 一次性事件（Toast），用 Channel 保证至少投递一次 */
    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // 冷启动时如果数据库为空，静默刷新一次（不弹 Toast）
        viewModelScope.launch {
            if (repo.latest() == null) refresh(silent = true)
        }
        // 监听"立即执行"任务的状态变化，完成后弹出 Toast
        viewModelScope.launch {
            var prevState: WorkInfo.State? = null
            WorkManager.getInstance(app)
                .getWorkInfosForUniqueWorkFlow("bing_wallpaper_now")
                .collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    val current = info.state
                    val wasActive = prevState == WorkInfo.State.RUNNING ||
                            prevState == WorkInfo.State.ENQUEUED ||
                            prevState == WorkInfo.State.BLOCKED
                    _ui.value = _ui.value.copy(
                        setting = current == WorkInfo.State.RUNNING ||
                                current == WorkInfo.State.ENQUEUED ||
                                current == WorkInfo.State.BLOCKED
                    )
                    if (current == WorkInfo.State.SUCCEEDED && wasActive) {
                        _events.trySend(getApplication<Application>().getString(R.string.msg_set_success))
                        if (info.outputData.getBoolean(SetWallpaperWorker.KEY_SAVED_NEW, false)) {
                            _events.trySend(getApplication<Application>().getString(R.string.msg_saved_to_gallery))
                        }
                    } else if ((current == WorkInfo.State.FAILED ||
                                current == WorkInfo.State.CANCELLED) && wasActive
                    ) {
                        _events.trySend(
                            getApplication<Application>().getString(
                                R.string.msg_set_fail, current.name.lowercase()
                            )
                        )
                    }
                    prevState = current
                }
        }
    }

    /** 刷新壁纸数据。silent=true 时失败不弹 Toast。 */
    fun refresh(silent: Boolean = false) {
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            runCatching { repo.refresh() }
                .onSuccess {
                    _ui.value = _ui.value.copy(loading = false)
                    // 刷新后触发 Wi-Fi 预取
                    WorkScheduler.enqueuePrefetch(getApplication())
                }
                .onFailure {
                    _ui.value = _ui.value.copy(loading = false)
                    if (!silent) toast(
                        getApplication<Application>().getString(R.string.msg_refresh_fail, it.message.orEmpty())
                    )
                }
        }
    }

    /**
     * 用户点击"设为壁纸"按钮。
     * 把指定日期传给 WorkManager，在后台下载并设置壁纸。
     */
    fun setAsWallpaper(entry: WallpaperEntity) {
        _ui.value = _ui.value.copy(setting = true)
        WorkScheduler.runOnce(getApplication(), requireUnmetered = false, date = entry.date)
        toast(getApplication<Application>().getString(R.string.msg_applying))
    }

    /** 正在下载的 Hero 图片任务，按日期去重 */
    private val heroJobs = mutableMapOf<String, Job>()

    /**
     * 为详情页预下载 UHD 大图，并暴露下载进度。
     * 同一日期只启动一次下载，多个观察者共享同一个进度流。
     */
    fun heroProgress(entry: WallpaperEntity): StateFlow<DownloadProgress> {
        val key = repo.uhdProgressKey(entry.date)
        if (heroJobs[entry.date]?.isActive != true) {
            heroJobs[entry.date] = viewModelScope.launch {
                runCatching { repo.ensureHeroImage(entry) }
            }
        }
        return repo.progressFlow(key)
    }

    /**
     * 观察（不触发）下载进度。供列表页卡片显示下载进度条。
     * 订阅不会启动下载，只是读取共享进度流。
     */
    fun progressOf(entry: WallpaperEntity): StateFlow<DownloadProgress> =
        repo.progressFlow(repo.uhdProgressKey(entry.date))

    fun uhdImage(entry: WallpaperEntity): WallpaperImage? = repo.uhdImageRef(entry)

    fun hasLocalUhd(entry: WallpaperEntity): Boolean = uhdImage(entry) != null

    fun startWifiPrefetchIfNeeded(entries: List<WallpaperEntity>) {
        if (!userSettings.value.prefetchOnWifi) return
        if (entries.none { !hasLocalUhd(it) }) return
        WorkScheduler.enqueuePrefetch(getApplication())
    }

    /** 分享 UHD 大图。先下载到本地，再通过系统分享菜单发送。 */
    fun share(entry: WallpaperEntity) {
        val ctx = getApplication<Application>()
        toast(ctx.getString(R.string.msg_share_prepare))
        viewModelScope.launch {
            runCatching { repo.ensureHeroImage(entry) }
                .onSuccess { image ->
                    try {
                        val uri = image.shareUri(ctx)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, entry.copyright)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(send, entry.title).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(chooser)
                    } catch (e: IllegalArgumentException) {
                        android.util.Log.w("BingViewModel", "FileProvider mapping failed", e)
                        toast(ctx.getString(R.string.msg_share_failed, e.message.orEmpty()))
                    } catch (e: android.content.ActivityNotFoundException) {
                        android.util.Log.w("BingViewModel", "No app can handle ACTION_SEND", e)
                        toast(ctx.getString(R.string.msg_share_failed, e.message.orEmpty()))
                    } catch (e: Exception) {
                        android.util.Log.w("BingViewModel", "share() failed", e)
                        toast(ctx.getString(R.string.msg_share_failed, e.message.orEmpty()))
                    }
                }
                .onFailure {
                    toast(ctx.getString(R.string.msg_share_failed, it.message.orEmpty()))
                }
        }
    }

    fun viewPortrait(entry: WallpaperEntity) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            runCatching { repo.ensurePortraitImage(entry) }
                .onSuccess { image ->
                    runCatching {
                        val i = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(image.shareUri(ctx), "image/jpeg")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(i)
                    }.onFailure {
                        toast(ctx.getString(R.string.msg_open_image_failed, it.message.orEmpty()))
                    }
                }
                .onFailure {
                    toast(ctx.getString(R.string.msg_open_image_failed, it.message.orEmpty()))
                }
        }
    }

    /** 拼接 UHD 原图地址 */
    fun originalUrl(entry: WallpaperEntity): String =
        BingApiFactory.buildImageUrl(entry.urlBase, "_UHD.jpg")

    /** 拼接带 HpDate filter 的 Bing 首页图片来源链接。 */
    fun sourceUrl(entry: WallpaperEntity): String {
        val separator = if (entry.copyrightLink.contains('?')) "&" else "?"
        val filters = "HpDate:\"${entry.startDate}_1600\"+mgzv3configlist:\"BingQA_Encyclopedia_Layout\""
        return entry.copyrightLink + separator + "filters=" + Uri.encode(filters, "+:")
    }

    /** 复制原图链接到系统剪贴板 */
    fun copyOriginalUrl(entry: WallpaperEntity) {
        val ctx = getApplication<Application>()
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText(entry.title, originalUrl(entry)))
        toast(ctx.getString(R.string.msg_link_copied))
    }

    /** 拼接缩略图地址（网格预览用） */
    fun previewUrl(entry: WallpaperEntity): String =
        BingApiFactory.buildImageUrl(entry.urlBase, "_640x360.jpg")

    private fun toast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun WallpaperImage.shareUri(ctx: Application): android.net.Uri {
        uri?.let { return it }
        val file = file ?: error("Wallpaper image has no file or uri")
        return androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
    }
}
