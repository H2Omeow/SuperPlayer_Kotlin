package top.nekoh2o.player

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.nekoh2o.player.data.cache.MusicCache
import top.nekoh2o.player.data.net.ApiFactory
import top.nekoh2o.player.data.net.CookieStore
import top.nekoh2o.player.data.repo.DownloadIndex
import top.nekoh2o.player.data.repo.LocalStore
import top.nekoh2o.player.data.repo.MusicRepository

class PlayerApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var localStore: LocalStore
        private set

    override fun onCreate() {
        super.onCreate()

        MusicCache.init(this)
        DownloadIndex.init(this)
        localStore = LocalStore(this)

        // CookieStore 必须先初始化完成，ApiFactory 的拦截器才能读到正确的 token
        appScope.launch {
            CookieStore.init(this@PlayerApp)

            // CookieStore 加载完成后再初始化 ApiFactory，确保拦截器能读到已保存的 token
            ApiFactory.init(this@PlayerApp)

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
