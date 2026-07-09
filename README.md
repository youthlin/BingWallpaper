# BingWallpaper 3.0 · 2026 翻新版

> 十年前 v2.1.3 的 Java + AlarmManager + SQLite 老工程，
> 重构为 Kotlin + Jetpack Compose + WorkManager 的现代化 Android 应用。
> 仅供个人使用，不上架应用商店。

## 如何导入 IDE 开始开发

1. 用 **Android Studio Ladybug (2024.2)** 及以上版本打开本项目根目录
2. 等待 Gradle 同步完成（首次需要下载依赖，可能需要几分钟）
3. 确保 JDK 版本为 **17**（File → Project Structure → SDK Location → Gradle JDK）
4. 连接模拟器或真机，点击 Run 按钮即可运行

**项目在 IDE 中打开后**，所有源码在 `app/src/main/java/com/youthlin/bingwallpaper/` 下。

## 软件入口和执行流程

### 启动流程

```
用户点击图标
  → BingApp.onCreate()          ← 应用入口，在 AndroidManifest.xml 中声明
      ├─ 创建通知渠道
      ├─ 恢复每日 AlarmManager 闹钟
      └─ 如果开启 Wi-Fi 预取，排入后台下载任务
  → MainActivity.onCreate()     ← 唯一 Activity
      └─ setContent { BingTheme { RootNav() } }
          └─ RootNav()          ← 导航根节点，管理两个页面
              ├─ "tabs"         ← 首页（壁纸网格 + 设置）
              └─ "detail/{id}"  ← 详情页（大图）
```

### 用户操作流程

```
【首页壁纸网格】
  HomeScreen → 观察 Room 数据库 → 显示缩略图列表
  点击卡片 → 导航到 DetailScreen(id=日期)
  下拉刷新 → BingViewModel.refresh() → 调用 Bing API → 存入 Room → UI 自动更新
  Wi-Fi 预取开启且有未缓存 UHD → 自动排入 PrefetchUhdWorker，下载中显示进度

【详情页】
  DetailScreen → 左右滑动浏览不同日期
  进入时自动下载 UHD 大图 → 未完成时显示缩略图 + 下载进度，完成后显示本地 UHD
  点击"设为壁纸" → BingViewModel.setAsWallpaper(entry) → WorkManager 后台下载+设置
  菜单 → 搜索图片 / 查看原图 / 复制链接 / 分享图片

【设置页】
  SettingsScreen → 修改 DataStore 偏好 → 自动同步每日闹钟 / WorkManager 调度
  立即执行 → WorkScheduler.runOnce() → WorkManager 后台执行
```

### 数据流（单向）

```
Bing API (JSON)
  → WallpaperRepository.refresh()     // 拉取元数据
  → Room upsert                        // 存入数据库
  → Flow 自动推送                       // Room 返回响应式 Flow
  → BingViewModel.wallpapers (StateFlow) // ViewModel 持有
  → Composable.collectAsStateWithLifecycle() // UI 订阅
  → 界面重组                             // 自动刷新
```

## 核心功能

- 拉取必应每日壁纸（近 8 天，`https://cn.bing.com/HPImageArchive.aspx`, UHD 分辨率）
- Compose 网格浏览 / 详情大图（支持双指缩放）
- 一键设为主屏 / 锁屏 / 双屏壁纸
- 每日定时自动更换（AlarmManager 到点触发，WorkManager 执行下载/设置）
- 开机后如果自动更换开启，会立即补执行一次并恢复下一次每日闹钟
- 竖屏设备设置壁纸时优先使用 Bing 竖屏构图 `_768x1366.jpg`，失败后退回 UHD 居中裁剪
- 设置：定时时间、Bing 市场 mkt、目标屏幕、Wi-Fi Only、保存图库、Wi-Fi 预取、立即执行、Material You 动态取色
- Wi-Fi 下自动预下载 UHD 大图；图库保存只保存 UHD 原图并做同名去重
- 分享图片（通过 FileProvider）

## 技术栈对比

| 维度 | 旧版 v2.1.3 (2016) | 新版 v3.0 (2025) |
|---|-------------------|-----------------|
| 语言 | Java 8 | Kotlin 2.1 |
| UI | Support v7 XML + GridView | Jetpack Compose Material3 + LazyVerticalGrid |
| 主题 | AppCompat | Material You 动态取色 (Android 12+) |
| 构建 | AGP 2.0.0 · jcenter · Groovy DSL | AGP 8.13 · Kotlin DSL · Version Catalog · KSP |
| 目标 SDK | min 14 / target 23 | min 26 / target 35 |
| 网络 | URL.openStream() HTTP 明文 | Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization，全 HTTPS |
| 图片加载 | BitmapFactory.decodeFile | Coil 2.7 |
| 存储 | 原生 SQLite + external storage | Room 2.6 + 应用私有目录（Scoped Storage 兼容） |
| 偏好 | SharedPreferences | DataStore-Preferences |
| 定时 | AlarmManager + IntentService + BootReceiver | AlarmManager + BroadcastReceiver 到点触发，WorkManager 负责网络约束/退避/执行 |
| 权限 | 多个（存储/开机/网络） | INTERNET / SET_WALLPAPER / POST_NOTIFICATIONS / RECEIVE_BOOT_COMPLETED |

## 目录结构

```
BingWallpaper/
├── settings.gradle.kts          # 仓库 + module 声明
├── build.gradle.kts             # 顶层插件声明
├── gradle/libs.versions.toml    # 版本目录（所有依赖唯一入口）
└── app/
    ├── build.gradle.kts         # 应用模块
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml  # 权限、Activity、Receiver、FileProvider
        ├── res/
        │   ├── values/          # strings.xml (zh) / themes.xml
        │   ├── values-en/       # 英文
        │   ├── mipmap-anydpi-v26/  # 自适应图标
        │   └── xml/             # file_paths / backup / dataExtraction rules
        └── java/com/youthlin/bingwallpaper/
            ├── BingApp.kt                     # Application 入口：通知渠道 + 恢复调度
            ├── MainActivity.kt                # 单 Activity + NavHost 导航
            ├── ui/
            │   ├── theme/Theme.kt             # Material3 动态取色
            │   ├── BingViewModel.kt           # ViewModel：数据 + 操作 + 一次性事件
            │   ├── HomeScreen.kt              # 壁纸网格列表
            │   ├── DetailScreen.kt            # 大图详情 + 下载进度 + 菜单
            │   └── SettingsScreen.kt          # 设置页面
            ├── data/
            │   ├── BingApi.kt                 # Retrofit 接口 + 工厂
            │   ├── BingModels.kt              # kotlinx.serialization DTO
            │   ├── DownloadProgress.kt        # 下载进度数据类
            │   ├── SettingsStore.kt           # DataStore 读写封装
            │   ├── WallpaperRepository.kt     # 数据仓库：网络+文件+WallpaperManager
            │   └── db/
            │       └── AppDatabase.kt         # Room：Entity + Dao + Database
            └── work/
                ├── SetWallpaperWorker.kt      # 设置壁纸后台任务
                ├── PrefetchUhdWorker.kt       # Wi-Fi 预下载后台任务
                ├── DailyWallpaperReceiver.kt  # 每日闹钟到点回调
                ├── BootReceiver.kt            # 开机后补执行 + 恢复闹钟
                └── WorkScheduler.kt           # 调度入口
```

## 关键实现说明

### 1. 每日调度：AlarmManager + WorkManager

当前源码采用混合方案：
- `WorkScheduler.apply()` 用 `AlarmManager.setAndAllowWhileIdle()` 排到用户设定的下一次 `hh:mm`
- `DailyWallpaperReceiver` 到点后调用 `WorkScheduler.runOnce()`，真正下载和设置仍由 `SetWallpaperWorker` 执行
- `SetWallpaperWorker` 使用 WorkManager 网络约束：Wi-Fi Only 对应 `UNMETERED`，否则 `CONNECTED`
- Worker 失败后使用指数退避重试，最多尝试 5 次
- 设备重启会清空 AlarmManager 闹钟，所以 `BootReceiver` 接收 `BOOT_COMPLETED` 后：
  - 如果自动更换开启，先立即补执行一次
  - 再按当前设置恢复下一次每日闹钟

这个方案不是绝对精确闹钟，但比 `PeriodicWorkRequest` 更接近用户设置的时刻，同时仍把重活交给 WorkManager。

### 2. 壁纸目标（主屏 / 锁屏 / 双屏）

```kotlin
val flag = when (target) {
    HOME -> WallpaperManager.FLAG_SYSTEM
    LOCK -> WallpaperManager.FLAG_LOCK
    BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
}
wm.setBitmap(bitmap, null, true, flag)
```

该重载自 API 24 引入，旧版只能设主屏。

### 3. Bing API

- 直接请求 `https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=<设置项>`，走 HTTPS
- `mkt` 默认由设备 Locale 推导，例如 `zh-CN` / `en-US`，也可在设置页手动输入
- 使用 `_UHD.jpg` 后缀（Bing 返回原图，一般 3840×2160）
- 缩略图走 `_640x360.jpg`，供网格快速预览
- 竖屏设置壁纸时尝试 `_768x1366.jpg`，使用 Bing 的竖屏构图版本

### 4. 存储

- 元数据：Room 表 `wallpapers`（date 主键 + urlBase / title / copyright / filePath）
- UHD 原图：应用私有目录 `filesDir/wallpapers/{date}_UHD.jpg`，旧路径 `{date}.jpg` 仍兼容读取
- 竖屏壁纸缓存：`filesDir/wallpapers/{date}_768x1366.jpg`，只用于设置壁纸，不写入图库
- 图库导出：通过 MediaStore API 写入 `Pictures/BingWallpaper/`，只保存 UHD 原图，同名已存在则跳过

### 5. 下载进度系统

每个图片下载对应一个共享的 StateFlow（key = `"{date}_UHD_jpg"`），多个观察者（网格卡片 + 详情页）看到同一个进度。详情页进入时触发 UHD 下载，下载中显示缩略图和 overlay，下载完成后切换为本地 UHD 文件。首页卡片在 UHD 未完成前标题显示浅色，完成后恢复正常颜色。

## 编译 & 运行

```bash
# 需要 JDK 17 + Android Studio Ladybug (2024.2) 及以上
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release 变体默认使用 debug 签名（个人使用够了）。

## 后续优化清单

- [ ] 通知渠道内的下载进度前台通知（当前只创建了 channel，未在 Worker 内 setForeground）
- [ ] 单元测试与 UI 测试（Room / Repo / Worker）
