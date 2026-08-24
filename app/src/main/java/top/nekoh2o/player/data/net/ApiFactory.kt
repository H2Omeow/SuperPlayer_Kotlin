package top.nekoh2o.player.data.net

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiFactory {

    const val BASE = "https://player.nekoh2o.top/"
    const val PLAYER_HOST = "player.nekoh2o.top"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    lateinit var cookieJar: PersistentCookieJar
        private set

    private lateinit var httpClient: OkHttpClient

    fun init(context: Context) {
        cookieJar = PersistentCookieJar(context.applicationContext)

        httpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // SSO 鉴权：仅对 player 域名注入 Bearer token（账户中心下发的 JWT），
            // 避免把 token 泄露给下载走的第三方 CDN 域名。
            .addInterceptor { chain ->
                val req = chain.request()
                val token = CookieStore.appTokenValue()
                if (req.url.host == PLAYER_HOST) {
                    android.util.Log.d("ApiFactory", "Interceptor for ${req.url} - token: ${token.take(20)}... (len=${token.length})")
                    if (token.isNotEmpty()) {
                        val resp = chain.proceed(
                            req.newBuilder()
                                .header("Authorization", "Bearer $token")
                                .build()
                        )
                        // 401 时记录完整 token 用于调试（不能读取 body，会导致流关闭）
                        if (resp.code == 401) {
                            android.util.Log.e("ApiFactory", "HTTP 401 for ${req.url}")
                            android.util.Log.e("ApiFactory", "Full token: $token")
                        }
                        resp
                    } else {
                        android.util.Log.w("ApiFactory", "Token is EMPTY for player API request!")
                        chain.proceed(req)
                    }
                } else {
                    chain.proceed(req)
                }
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }

    private fun retrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json".toMediaType()
                )
            )
            .build()
    }

    val music: MusicApi by lazy {
        retrofit(BASE).create(MusicApi::class.java)
    }

    val user: UserApi by lazy {
        retrofit(BASE).create(UserApi::class.java)
    }

    fun client(): OkHttpClient = httpClient
}
