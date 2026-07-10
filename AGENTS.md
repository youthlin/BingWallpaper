# BingWallpaper 项目架构概览

## 项目概述

必应每日壁纸自动更换 Android 应用（个人使用，不上架商店）。从 2016 年 Java + AlarmManager 老工程重写为 Kotlin + Jetpack Compose + WorkManager 的现代化版本；当前每日定时采用 AlarmManager 到点触发、WorkManager 执行重活的混合方案。

- **package**: `com.youthlin.bingwallpaper`
- **minSdk / targetSdk**: 26 / 35
- **语言**: Kotlin 2.1
- **UI**: Jetpack Compose + Material 3 + Material You 动态取色
- **构建**: AGP 8.13.x + Gradle Kotlin DSL + Version Catalog + KSP
- **架构**: 单 Activity + Compose Navigation + MVVM (AndroidViewModel + StateFlow)

## 架构设计

### 层级结构

```
ui/          ← Compose 页面 + ViewModel（只读 StateFlow，不持有可变状态）
data/        ← Repository（网络+Room+文件）、DataStore 偏好、API 模型
work/        ← WorkManager CoroutineWorker + 调度器
```

### 数据流（单向）

```
Bing API → Retrofit → BingArchiveResponse (DTO)
  → WallpaperRepository.refresh() → Room upsert → Flow 自动推送
  → BingViewModel.wallpapers (StateFlow<List<WallpaperEntity>>)
  → Compose collectAsStateWithLifecycle() → UI 重组
```

### 关键组件

| 组件 | 职责 |
|------|------|
| `BingApp` | Application 子类，创建通知渠道，启动时恢复每日闹钟 + Wi-Fi 预取 |
| `MainActivity` | 唯一 Activity，edge-to-edge + NavHost（tabs / detail 两屏） |
| `BingViewModel` | 列表 + 下载进度 + 设为壁纸 + 分享 + 一次性 Toast 事件 |
| `WallpaperRepository` | 中心枢纽：API 刷新、文件下载、下载进度流、WallpaperManager 设置、图库保存 |
| `WorkScheduler` | 调度入口，含每日 AlarmManager 闹钟、`runOnce()`、`enqueuePrefetch()` |
| `SettingsStore` | DataStore-Preferences 包装，Flow<UserSettings> 响应式读取 |
| `DailyWallpaperReceiver` | 每日闹钟到点回调，启动一次性 WorkManager 任务并排下一次闹钟 |
| `BootReceiver` | 开机后恢复每日闹钟；自动更换开启时会立即补执行一次 |

### 导航结构

- `NavHost("tabs" / "detail/{id}")` — 底部 Tab 使用 HorizontalPager 切换"壁纸/设置"
- 详情页使用 HorizontalPager 左右滑动浏览每日壁纸

### 下载进度系统

`WallpaperRepository` 内维护 `ConcurrentHashMap<String, MutableStateFlow<DownloadProgress>>`，以 `"{date}_UHD_jpg"` 为 key。多个观察者（网格卡片 + 详情页）共享同一进度流，初始值为 `done=true` 避免误显示 "0%" 遮罩。`ensureVariant()` 在 Mutex 保护下执行实际下载，逐步更新进度。详情页显示源为本地 UHD 文件；未下载完成前使用缩略图占位，避免 Coil 和 Repository 各自下载一遍 UHD。

## 构建与运行

```bash
# 需要 JDK 17 + Android Studio Ladybug (2024.2) 及以上
./gradlew assembleDebug         # 编译 debug APK
./gradlew assembleRelease       # 编译 release（默认 debug 签名；可用 local.properties 配旧 key）
./gradlew clean                 # 清理
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**版本号**: `versionCode` / `versionName` 定义在 `app/build.gradle.kts` 的 `defaultConfig` 中。

**依赖管理**: 所有版本号集中在 `gradle/libs.versions.toml`，使用 `alias(libs.plugins.xxx)` 和 `implementation(libs.xxx.xxx)` 引用。

## 代码风格与约定

- **Kotlin 风格**: `kotlin.code.style=official`
- **包结构**: `ui/`、`data/`、`work/` 三层，`data/db/` 存放 Room 相关
- **ViewModel**: 使用 `AndroidViewModel`（需要 Context），通过 `viewModel()` 在 Composable 中获取
- **Composable**: 私有函数不加 `@Composable` 注解重复说明，函数参数优先使用命名参数
- **字符串**: 全部硬编码到 `res/values/strings.xml`（中文）+ `res/values-en/strings.xml`（英文），通过 `R.string.xxx` 引用
- **API 模型**: `kotlinx.serialization`，`@SerialName` 映射 JSON 字段，`Json { ignoreUnknownKeys = true }`
- **文件路径**:
  - 未保存到图库时，UHD 原图下载到 `context.filesDir/wallpapers/{date}_UHD.jpg`（旧 `{date}.jpg` 兼容读取）
  - 未保存到图库时，竖屏壁纸缓存下载到 `context.filesDir/wallpapers/{date}_768x1366.jpg`
  - 保存到图库时，UHD 原图和 768x1366 竖屏图可分别勾选；成功写入 MediaStore 后删除对应内部缓存，后续使用图库 Uri
- **FileProvider**: `file_paths.xml` 映射 `files-path name="wallpapers"`，用于分享功能
- **Release 签名**: `app/build.gradle.kts` 可从 `local.properties` 读取 `releaseStoreFile` / `releaseStorePassword` / `releaseKeyAlias` / `releaseKeyPassword`；未配置时 fallback 到 debug signing。签名密码不要提交。

## 权限

当前声明 5 个权限，无运行时权限请求：
- `INTERNET` — 网络访问
- `SET_WALLPAPER` — 设置壁纸（无需运行时授权）
- `POST_NOTIFICATIONS` — 通知渠道（Android 13+ 需在 Worker 内 `setForeground`，当前未实现）
- `RECEIVE_BOOT_COMPLETED` — 开机后恢复每日闹钟；自动更换开启时补执行一次
- `SCHEDULE_EXACT_ALARM` — 尽量按用户设置时间触发每日闹钟；不可用时回退到非精确闹钟

## 定时与后台调度逻辑

- **每日定时**: `WorkScheduler.apply()` 优先使用 `AlarmManager.setExactAndAllowWhileIdle()` 排到下一次用户设定的 `hh:mm`；没有精确闹钟权限时回退到 `setAndAllowWhileIdle()`
- **到点执行**: `DailyWallpaperReceiver` 调用 `WorkScheduler.runOnce()`，真正下载和设置由 `SetWallpaperWorker` 执行
- **开机补偿**: `BootReceiver` 在 `BOOT_COMPLETED` 后读取设置；若自动更换开启，立即补执行一次，并恢复下一次每日闹钟
- **网络约束**: 设置壁纸的一次性 WorkManager 任务只要求 `CONNECTED`；Wi-Fi Only 在 `SetWallpaperWorker` 内用 `NetworkCapabilities.TRANSPORT_WIFI/ETHERNET` 判断，避免部分 ROM 将 Wi-Fi 标为 metered 后一直不运行
- **失败重试**: `BackoffPolicy.EXPONENTIAL, 15min`，Worker 内 `runAttemptCount < 5` 限制
- **Wi-Fi 预取**: `PrefetchUhdWorker` 受 `UNMETERED` 约束，遍历所有条目下载 UHD 变体，可选保存到图库

## 当前实现要点

- `PrefetchUhdWorker` + `WorkScheduler.enqueuePrefetch()` — Wi-Fi 下自动预取大图
- `WallpaperRepository.ensureVariant()` / `ensureHeroFile()` — 下载进度追踪
- `WallpaperRepository.ensureSelectedGalleryImages()` — 按设置将 UHD/竖屏图写入 MediaStore；保存成功后对应内部缓存不再保留
- `DownloadProgress` 数据类 + `progressFlow()` — 多观察者共享下载进度
- `SetWallpaperWorker.KEY_SAVED_NEW` — 输出数据标记是否新增图库保存
- `BingViewModel.heroProgress()` / `progressOf()` / `share()` — 详情页下载进度 + 分享
- `WallpaperRepository.applyBestFit()` — 竖屏设备优先使用 Bing `_768x1366.jpg` 竖屏构图，失败回退 UHD 居中裁剪
- `SettingsStore.market` — Bing API `mkt` 默认来自设备 Locale，也可在设置页手动输入
- `SettingsStore.saveUhdToGallery` / `savePortraitToGallery` — 控制自动保存图库时保存哪些规格
- 首页网格 `GridCells.Adaptive(160.dp)`；标题固定单行高度，UHD 未完成前浅色显示

## 安全注意事项

- 所有网络请求走 HTTPS（`https://www.bing.com/`）
- API 响应解析使用 `ignoreUnknownKeys = true`，避免新增字段崩溃
- 图片存储使用应用私有目录，不暴露到外部存储
- FileProvider 仅映射 `filesDir/wallpapers/` 目录
- Release 构建启用 ProGuard 混淆 + 资源压缩
- 无用户输入，无 SQL 注入风险（Room 使用参数化查询）
