package top.nekoh2o.player.data.net

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

class KugouInterceptor(private val sessions: KugouSessionStore) : Interceptor {
    private val clock = AtomicLong()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val platform = request.url.queryParameter("platform")?.toIntOrNull() ?: 0
        require(platform in 0..1)
        // The API caches by URL, including POSTs and QR status requests.
        val timestamp = clock.updateAndGet { maxOf(System.currentTimeMillis(), it + 1) }
        val url = request.url.newBuilder().setQueryParameter("timestamp", timestamp.toString()).build()
        val next = request.newBuilder().url(url)
            .removeHeader("Authorization")
            .header("Cookie", sessions.cookie(platform))
            .header("Cache-Control", "no-store").build()
        val response = chain.proceed(next)
        // Login credentials are committed only after the repository validates success.
        sessions.mergeResponse(platform, url, response.headers.values("Set-Cookie"))
        return response
    }
}
