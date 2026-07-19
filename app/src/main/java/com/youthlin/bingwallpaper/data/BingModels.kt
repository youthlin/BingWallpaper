package com.youthlin.bingwallpaper.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 必应 API 返回的 JSON 对应的 Kotlin 数据类（DTO，数据传输对象）。
 * 只定义我们关心的字段，其余字段会被自动忽略（Json.ignoreUnknownKeys = true）。
 *
 * 调用链：BingApi.archive() → BingArchiveResponse → WallpaperRepository.refresh() → Room 存入数据库
 */

/** API 返回的顶层 JSON */
@Serializable
data class BingArchiveResponse(
    val images: List<BingImage> = emptyList()
)

/** 单张壁纸信息 */
@Serializable
data class BingImage(
    /** 开始日期，如 "20250706" */
    @SerialName("startdate") val startDate: String,
    /** 结束日期，如 "20250706"（我们用它作为唯一标识） */
    @SerialName("enddate") val endDate: String,
    /**
     * 图片相对路径，如 "/th?id=OHR.MilkyBridge_ZH-CN9834172781_1920x1080.jpg&rf=LaDigue_UHD.jpg"
     */
    val url: String,
    /**
     * 不带尺寸后缀的基础 URL，如 "/th?id=OHR.MilkyBridge_ZH-CN9834172781"
     * 拼接 _UHD.jpg 得到高清大图，拼接 _640x360.jpg 得到缩略图
     */
    @SerialName("urlbase") val urlBase: String,
    /** 版权信息 */
    val copyright: String,
    /** 版权链接（Bing 搜索页） */
    @SerialName("copyrightlink") val copyrightLink: String,
    /** 标题，API 可能返回空字符串 */
    val title: String = ""
)