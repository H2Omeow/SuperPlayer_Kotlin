package top.nekoh2o.player.data.net

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import top.nekoh2o.player.desktop.*
import top.nekoh2o.player.data.net.nativeapi.*

object ApiFactory {
    const val BASE = "https://player.nekoh2o.top/"
    const val PLAYER_HOST = "player.nekoh2o.top"
    const val ANIMEMUSIC_BASE = "https://nekoh2o.top/api/music-source/"
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val publicClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()
    private val siteClient = publicClient.newBuilder().followRedirects(false).addInterceptor { chain ->
        val request = chain.request()
        val token = CookieStore.appTokenValue()
        chain.proceed(if (request.url.isHttps && request.url.host == PLAYER_HOST && token.isNotBlank())
            request.newBuilder().header("Authorization", "Bearer " + token).build() else request)
    }.build()
    private val ncClient = publicClient.newBuilder().followRedirects(false).addInterceptor(
        NeteaseNativeInterceptor(FilePreferences(DesktopPaths.home.resolve("netease.properties")))).build()
    private val kgClient = publicClient.newBuilder().followRedirects(false)
        .addInterceptor(KugouInterceptor(CookieStore.kgSessions))
        .addInterceptor(KugouNativeInterceptor(CookieStore.kgSessions)).build()
    private fun retrofit(client: OkHttpClient) = Retrofit.Builder().baseUrl(BASE).client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
    private val sourceClient = publicClient.newBuilder().followRedirects(false)
        .addInterceptor(SourceGatewayInterceptor(publicClient, CookieStore::appTokenValue)).build()
    private fun animemusicRetrofit() = Retrofit.Builder().baseUrl(ANIMEMUSIC_BASE).client(sourceClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
    val music: MusicApi by lazy { retrofit(ncClient).create(MusicApi::class.java) }
    val nativeMusic: ProviderApi by lazy { retrofit(ncClient).create(ProviderApi::class.java) }
    val netease: NeteaseApi by lazy { retrofit(ncClient).create(NeteaseApi::class.java) }
    val kugou: KugouApi by lazy { retrofit(kgClient).create(KugouApi::class.java) }
    val user: UserApi by lazy { retrofit(siteClient).create(UserApi::class.java) }
    val mv: top.nekoh2o.player.data.repo.MvApi by lazy {
        Retrofit.Builder().baseUrl("https://nekoh2o.top/api/media/").client(sourceClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(top.nekoh2o.player.data.repo.MvApi::class.java)
    }
    val animemusic: AnimemusicApi by lazy { animemusicRetrofit().create(AnimemusicApi::class.java) }
    fun client() = siteClient
    fun mediaClient() = publicClient.newBuilder().callTimeout(0, TimeUnit.SECONDS).build()
    suspend fun awaitReady() = Unit
}
