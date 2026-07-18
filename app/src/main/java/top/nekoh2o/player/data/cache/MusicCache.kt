package top.nekoh2o.player.data.cache

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File

object MusicCache {

    private const val MAX_CACHE_BYTES = 2L * 1024L * 1024L * 1024L

    @Volatile
    private var instance: SimpleCache? = null

    @Synchronized
    fun init(context: Context) {
        if (instance != null) return

        val appContext = context.applicationContext
        val cacheDirectory = File(appContext.cacheDir, "music")

        instance = SimpleCache(
            cacheDirectory,
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(appContext)
        )
    }

    fun dataSourceFactory(
        upstreamFactory: DataSource.Factory
    ): DataSource.Factory {
        val cache = checkNotNull(instance) {
            "MusicCache 尚未初始化，请先调用 MusicCache.init(context)"
        }

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun cacheSpace(): Long = instance?.cacheSpace ?: 0L

    fun cachedKeys(): Set<String> = instance?.keys?.toSet() ?: emptySet()

    @Synchronized
    fun remove(key: String) {
        val cache = instance ?: return
        runCatching {
            cache.removeResource(key)
        }
    }

    @Synchronized
    fun clear() {
        val cache = instance ?: return
        cache.keys.toList().forEach { key ->
            runCatching {
                cache.removeResource(key)
            }
        }
    }
}
