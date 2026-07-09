package com.youthlin.bingwallpaper.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * 必应壁纸 API 接口。
 * 请求地址：https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=zh-CN
 * 返回 JSON 格式的最近 8 天壁纸信息。
 */
interface BingApi {
    /**
     * @param n 请求数量（1~8，Bing 上限 8 张）
     * @param idx 从今天往前偏移几天（0 = 今天）
     * @param mkt 市场/语言
     */
    @GET("HPImageArchive.aspx?format=js")
    suspend fun archive(
        @Query("n") n: Int = 8,
        @Query("idx") idx: Int = 0,
        @Query("mkt") mkt: String = "zh-CN"
    ): BingArchiveResponse
}

/**
 * 创建 BingApi 实例的工厂，同时提供图片 URL 拼接工具方法。
 */
object BingApiFactory {
    private const val BASE_URL = "https://cn.bing.com/"

    private val json = Json {
        ignoreUnknownKeys = true // 忽略 API 新增的未知字段，避免崩溃
        isLenient = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    val instance: BingApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BingApi::class.java)
    }

    /**
     * 把 Bing 的 urlBase 拼成完整的图片下载地址。
     * 例如 urlBase="/th?id=OHR.xxx"，suffix="_UHD.jpg"
     * → "https://cn.bing.com/th?id=OHR.xxx_UHD.jpg"
     */
    fun buildImageUrl(urlBase: String, suffix: String = "_UHD.jpg"): String {
        val base = if (urlBase.startsWith("http")) urlBase else BASE_URL.trimEnd('/') + urlBase
        return base + suffix
    }
}