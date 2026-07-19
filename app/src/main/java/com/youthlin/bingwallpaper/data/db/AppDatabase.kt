package com.youthlin.bingwallpaper.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 壁纸数据库实体。Room 会自动建表 "wallpapers"。
 * date 是主键（Bing 的 endDate，格式 yyyyMMdd），保证每天只有一条记录。
 */
@Entity(tableName = "wallpapers")
data class WallpaperEntity(
    /** 日期，格式 yyyyMMdd，作为主键 */
    @PrimaryKey val date: String,
    /** Bing startdate，构造首页图片来源链接时用于 HpDate */
    @ColumnInfo(name = "start_date") val startDate: String = date,
    /** 图片 URL 基础部分（不含尺寸后缀） */
    @ColumnInfo(name = "url_base") val urlBase: String,
    /** 标题 */
    val title: String,
    /** 版权信息 */
    val copyright: String,
    /** 版权链接（Bing 搜索页） */
    @ColumnInfo(name = "copyright_link") val copyrightLink: String,
    /** 本地缓存文件路径，null 表示还没下载 */
    @ColumnInfo(name = "file_path") val filePath: String? = null,
    /** 入库时间 */
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** 数据库操作接口，Room 自动生成实现 */
@Dao
interface WallpaperDao {
    /** 观察所有壁纸，按日期降序，数据变化时自动通知 UI */
    @Query("SELECT * FROM wallpapers ORDER BY date DESC")
    fun observeAll(): Flow<List<WallpaperEntity>>

    /** 获取最新一条壁纸 */
    @Query("SELECT * FROM wallpapers ORDER BY date DESC LIMIT 1")
    suspend fun latest(): WallpaperEntity?

    /** 按日期查找 */
    @Query("SELECT * FROM wallpapers WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: String): WallpaperEntity?

    /** 更新或插入一条记录 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WallpaperEntity)

    /** 批量插入，已存在的跳过 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entities: List<WallpaperEntity>)

    /**
     * 刷新 API 元数据，但保留已下载/已入图库的 file_path。
     * SQLite UPSERT 在 Room 2.6 可直接执行。
     */
    @Query(
        """
        INSERT INTO wallpapers(date, start_date, url_base, title, copyright, copyright_link, file_path, created_at)
        VALUES(:date, :startDate, :urlBase, :title, :copyright, :copyrightLink, null, :createdAt)
        ON CONFLICT(date) DO UPDATE SET
            start_date = excluded.start_date,
            url_base = excluded.url_base,
            title = excluded.title,
            copyright = excluded.copyright,
            copyright_link = excluded.copyright_link
        """
    )
    suspend fun upsertMetadata(
        date: String,
        startDate: String,
        urlBase: String,
        title: String,
        copyright: String,
        copyrightLink: String,
        createdAt: Long
    )

    suspend fun upsertMetadata(entities: List<WallpaperEntity>) {
        entities.forEach {
            upsertMetadata(
                date = it.date,
                startDate = it.startDate,
                urlBase = it.urlBase,
                title = it.title,
                copyright = it.copyright,
                copyrightLink = it.copyrightLink,
                createdAt = it.createdAt
            )
        }
    }
}

/** Room 数据库定义 */
@Database(
    entities = [WallpaperEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wallpapers(): WallpaperDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** 获取数据库单例 */
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "bing.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN start_date TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE wallpapers SET start_date = date WHERE start_date = ''")
            }
        }
    }
}
