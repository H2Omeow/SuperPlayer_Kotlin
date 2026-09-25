package top.nekoh2o.player.desktop

import top.nekoh2o.player.data.model.UserData
import top.nekoh2o.player.data.net.ApiFactory
import top.nekoh2o.player.data.net.CookieStore

internal data class CloudCredentials(
    val ncCookie: String,
    val kgCookies: List<String>,
    val kgPlatform: Int
) {
    companion object {
        fun merge(local: CloudCredentials, remote: UserData): CloudCredentials {
            val remoteCookies = List(2) { remote.kgCookies.getOrNull(it).orEmpty() }
            val mergedCookies = List(2) { index ->
                local.kgCookies.getOrNull(index).orEmpty().ifBlank { remoteCookies[index] }
            }.toMutableList()
            if (mergedCookies[remote.kgPlatform.coerceIn(0, 1)].isBlank() && remote.kgToken.isNotBlank()) {
                mergedCookies[remote.kgPlatform.coerceIn(0, 1)] = "token=" + remote.kgToken
            }
            val hasLocalKugou = local.kgCookies.any(String::isNotBlank)
            return CloudCredentials(
                ncCookie = local.ncCookie.ifBlank { remote.ncCookie },
                kgCookies = mergedCookies,
                kgPlatform = if (hasLocalKugou) local.kgPlatform else remote.kgPlatform.coerceIn(0, 1)
            )
        }
    }
}

internal enum class CredentialSyncMode { FILL_MISSING, LOCAL_AUTHORITATIVE }

internal object DesktopCloudSync {
    suspend fun sync(
        library: Library = Library(),
        credentialMode: CredentialSyncMode = CredentialSyncMode.FILL_MISSING
    ): UserData {
        val response = ApiFactory.user.pullData()
        require(response.code == 0) { "云端数据读取失败，请稍后重试" }
        val remote = response.data ?: UserData()
        val localCredentials = localCredentials()
        val credentials = if (credentialMode == CredentialSyncMode.LOCAL_AUTHORITATIVE) {
            localCredentials
        } else {
            CloudCredentials.merge(localCredentials, remote)
        }
        applyCredentials(credentials)
        library.merge(remote)
        val merged = remote.copy(
            history = library.data.history,
            favorites = library.data.favorites,
            playlists = library.data.playlists,
            ncCookie = credentials.ncCookie,
            kgToken = CookieStore.kgTokenValue(credentials.kgPlatform),
            kgPlatform = credentials.kgPlatform,
            kgCookies = credentials.kgCookies
        )
        val pushed = ApiFactory.user.pushData(merged)
        require(pushed.code == 0) { "云端未确认数据同步成功" }
        return merged
    }

    private fun localCredentials() = CloudCredentials(
        ncCookie = CookieStore.userCookieValue(),
        kgCookies = List(2, CookieStore::kgCookieValue),
        kgPlatform = CookieStore.kgPlatformValue()
    )

    private suspend fun applyCredentials(credentials: CloudCredentials) {
        if (CookieStore.userCookieValue().isBlank() && credentials.ncCookie.isNotBlank()) {
            CookieStore.setUserCookie(credentials.ncCookie)
        }
        credentials.kgCookies.forEachIndexed { platform, cookie ->
            if (CookieStore.kgCookieValue(platform).isBlank() && cookie.isNotBlank()) {
                if (cookie.contains("userid=")) CookieStore.kgSessions.importCookie(platform, cookie)
                else CookieStore.kgSessions.merge(platform, mapOf("token" to cookie.removePrefix("token=")))
            }
        }
        CookieStore.setKgPlatform(credentials.kgPlatform)
    }
}
