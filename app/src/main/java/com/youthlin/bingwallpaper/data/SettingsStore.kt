package com.youthlin.bingwallpaper.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 壁纸设置目标 */
enum class WallpaperTarget { HOME, LOCK, BOTH }

/** 用户设置数据，通过 DataStore 持久化到本地文件 */
data class UserSettings(
    val autoDaily: Boolean = false,       // 是否每日自动更换
    val hour: Int = 8,                    // 自动更换时间（小时）
    val minute: Int = 0,                  // 自动更换时间（分钟）
    val onlyWifi: Boolean = true,         // 仅 Wi-Fi 下更新
    val target: WallpaperTarget = WallpaperTarget.BOTH, // 壁纸目标（主屏/锁屏/双屏）
    val saveToGallery: Boolean = true,    // 自动保存到系统图库
    val prefetchOnWifi: Boolean = false,  // Wi-Fi 下自动预下载大图
    val market: String = SettingsStore.defaultMarket() // Bing API 市场，如 zh-CN / en-US
)

/**
 * 用户设置读写封装。
 * 读写都通过 DataStore 的 Flow，响应式更新。
 */
class SettingsStore(private val context: Context) {

    /** 读取设置的 Flow，Compose 中可用 collectAsState() 订阅 */
    val flow: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            autoDaily = p[KEY_AUTO_DAILY] ?: false,
            hour = p[KEY_HOUR] ?: 8,
            minute = p[KEY_MINUTE] ?: 0,
            onlyWifi = p[KEY_ONLY_WIFI] ?: true,
            target = WallpaperTarget.entries.getOrElse(p[KEY_TARGET] ?: 2) { WallpaperTarget.BOTH },
            saveToGallery = p[KEY_SAVE_GALLERY] ?: true,
            prefetchOnWifi = p[KEY_PREFETCH_WIFI] ?: false,
            market = normalizeMarket(p[KEY_MARKET] ?: defaultMarket())
        )
    }

    /** 一次性读取当前设置 */
    suspend fun current(): UserSettings = flow.first()

    /** 以下为各设置项的写入方法 */
    suspend fun setAutoDaily(enabled: Boolean) = context.dataStore.edit { it[KEY_AUTO_DAILY] = enabled }
    suspend fun setTime(hour: Int, minute: Int) = context.dataStore.edit {
        it[KEY_HOUR] = hour; it[KEY_MINUTE] = minute
    }
    suspend fun setOnlyWifi(only: Boolean) = context.dataStore.edit { it[KEY_ONLY_WIFI] = only }
    suspend fun setTarget(target: WallpaperTarget) =
        context.dataStore.edit { it[KEY_TARGET] = target.ordinal }
    suspend fun setSaveToGallery(save: Boolean) =
        context.dataStore.edit { it[KEY_SAVE_GALLERY] = save }
    suspend fun setPrefetchOnWifi(enabled: Boolean) =
        context.dataStore.edit { it[KEY_PREFETCH_WIFI] = enabled }
    suspend fun setMarket(market: String) =
        context.dataStore.edit { it[KEY_MARKET] = normalizeMarket(market) }
    suspend fun resetMarketToDevice() =
        context.dataStore.edit { it.remove(KEY_MARKET) }

    companion object {
        fun defaultMarket(): String {
            val locale = Locale.getDefault()
            val language = locale.language.ifBlank { "en" }.lowercase(Locale.US)
            val country = locale.country.ifBlank {
                if (language == "zh") "CN" else "US"
            }.uppercase(Locale.US)
            return "$language-$country"
        }

        fun normalizeMarket(raw: String): String {
            val parts = raw.trim()
                .replace('_', '-')
                .split('-')
                .filter { it.isNotBlank() }
            if (parts.isEmpty()) return defaultMarket()

            val language = parts.first().lowercase(Locale.US)
            val country = parts.drop(1).lastOrNull()?.uppercase(Locale.US)
                ?: defaultMarket().substringAfter('-')
            return "$language-$country"
        }

        private val KEY_AUTO_DAILY = booleanPreferencesKey("auto_daily")
        private val KEY_HOUR = intPreferencesKey("hour")
        private val KEY_MINUTE = intPreferencesKey("minute")
        private val KEY_ONLY_WIFI = booleanPreferencesKey("only_wifi")
        private val KEY_TARGET = intPreferencesKey("target")
        private val KEY_SAVE_GALLERY = booleanPreferencesKey("save_gallery")
        private val KEY_PREFETCH_WIFI = booleanPreferencesKey("prefetch_wifi")
        private val KEY_MARKET = stringPreferencesKey("market")
    }
}
