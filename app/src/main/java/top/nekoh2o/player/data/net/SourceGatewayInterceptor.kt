package top.nekoh2o.player.data.net

import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class SourceGatewayInterceptor(private val client: OkHttpClient, private val token: () -> String) : Interceptor {
    private data class Session(val token: String, val id: String, val key: ByteArray, val expires: Long)
    private var session: Session? = null

    @Synchronized private fun credentials(value: String, base: String): Session {
        session?.takeIf { it.token == value && it.expires > System.currentTimeMillis() + 60000 }?.let { return it }
        val request = Request.Builder().url(base + "session").header("Authorization", "Bearer $value")
            .post(ByteArray(0).toRequestBody()).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("本站登录已失效或音源服务不可用，请重新登录后重试")
            val body = Json.parseToJsonElement(response.body?.string() ?: "{}").jsonObject
            val key = body.getValue("key").jsonPrimitive.content.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            Session(value, body.getValue("keyId").jsonPrimitive.content, key, body.getValue("expires").jsonPrimitive.content.toLong())
                .also { session = it }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val value = token()
        if (value.isBlank()) throw IOException("请先登录本站账号后使用惜缘惜梦音源")
        if (!request.url.isHttps || request.url.host != "nekoh2o.top" ||
            (!request.url.encodedPath.startsWith("/api/music-source/") && !request.url.encodedPath.startsWith("/api/media/"))) {
            throw IOException("音源请求地址无效")
        }
        val auth = credentials(value, "https://nekoh2o.top/api/music-source/")
        val timestamp = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val path = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
        val emptyHash = MessageDigest.getInstance("SHA-256").digest(ByteArray(0)).joinToString("") { "%02x".format(it) }
        val canonical = listOf(request.method, path, timestamp, nonce, emptyHash).joinToString("\n")
        val signature = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(auth.key, "HmacSHA256")) }
            .doFinal(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val response = chain.proceed(request.newBuilder().header("Authorization", "Bearer $value")
            .header("X-Source-Key", auth.id).header("X-Source-Time", timestamp)
            .header("X-Source-Nonce", nonce).header("X-Source-Signature", signature).build())
        if (response.code == 401) synchronized(this) { session = null }
        return response
    }
}
