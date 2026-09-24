package top.nekoh2o.player.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.net.CookieStore
import top.nekoh2o.player.data.repo.KugouRepository

class KugouAccountViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = KugouRepository()
    suspend fun getRecommendSongs(): List<Song> = repository.getRecommendSongs()
    suspend fun search(keyword: String, page: Int): List<Song> = repository.search(keyword, page)
    suspend fun searchSuggest(keyword: String): List<String> = repository.searchSuggest(keyword)
    fun getCurrentPlatform(): Int = CookieStore.kgPlatformValue()
}
