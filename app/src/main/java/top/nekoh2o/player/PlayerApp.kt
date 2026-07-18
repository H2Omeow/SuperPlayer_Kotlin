package top.nekoh2o.player

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.nekoh2o.player.data.net.ApiFactory
import top.nekoh2o.player.data.net.CookieStore
import top.nekoh2o.player.data.repo.LocalStore
import top.nekoh2o.player.data.repo.MusicRepository

class PlayerApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 全局本地存储（历史/收藏/自定义歌单）
    lateinit var localStore: LocalStore
        private set

    override fun onCreate() {
        super.onCreate()

        ApiFactory.init(this)
        localStore = LocalStore(this)

        appScope.launch {
            CookieStore.init(this@PlayerApp)
            if (!CookieStore.hasAnyCookie()) {
                MusicRepository().ensureGuestCookie()
            }
        }
    }

    companion object {
        lateinit var instance: PlayerApp
            private set
    }

    init {
        @Suppress("LeakingThis")
        instance = this
    }
}
