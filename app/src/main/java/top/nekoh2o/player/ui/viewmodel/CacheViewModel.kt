package top.nekoh2o.player.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import top.nekoh2o.player.data.cache.MusicCache
import top.nekoh2o.player.data.model.CachedItem

/**
 * 缓存管理 ViewModel
 * 负责：缓存列表查询、缓存清理
 */
class CacheViewModel(app: Application) : AndroidViewModel(app) {

    /**
     * 获取所有缓存项
     */
    fun getCachedKeys(): Set<String> {
        return MusicCache.cachedKeys()
    }

    /**
     * 删除指定的缓存项
     */
    fun removeCache(keys: Set<String>): Int {
        keys.forEach { MusicCache.remove(it) }
        return keys.size
    }

    /**
     * 清空所有缓存
     */
    fun clearAllCache() {
        MusicCache.clear()
    }
}
