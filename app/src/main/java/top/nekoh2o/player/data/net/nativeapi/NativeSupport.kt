package top.nekoh2o.player.data.net.nativeapi

import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal fun value(v: Any?): JsonElement = when(v) {
    null -> JsonNull; is JsonElement -> v; is Boolean -> JsonPrimitive(v); is Number -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to value(it.value) })
    is Iterable<*> -> JsonArray(v.map(::value)); else -> JsonPrimitive(v.toString())
}
internal fun obj(vararg fields: Pair<String, Any?>) = value(linkedMapOf(*fields)) as JsonObject
internal fun parameters(request: Request): Map<String, String> = buildMap {
    request.url.queryParameterNames.forEach { put(it, request.url.queryParameter(it).orEmpty()) }
    (request.body as? FormBody)?.let { body -> repeat(body.size) { put(body.name(it), body.value(it)) } }
}
internal fun jsonResponse(request: Request, body: JsonObject, code: Int = 200): Response = Response.Builder()
    .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Native provider")
    .header("Content-Type", "application/json").body(body.toString().toResponseBody("application/json".toMediaType())).build()
internal fun qrImage(url: String): String {
    val matrix = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 320, 320)
    val pixels = IntArray(320 * 320) { i -> if (matrix[i % 320, i / 320]) 0xff000000.toInt() else -1 }
    val bitmap = Bitmap.createBitmap(pixels, 320, 320, Bitmap.Config.ARGB_8888)
    return try { val bytes = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        "data:image/png;base64," + Crypto.b64(bytes.toByteArray()) } finally { bitmap.recycle() }
}
internal object Crypto {
    private val random = SecureRandom()
    fun randomHex(bytes: Int) = ByteArray(bytes).also(random::nextBytes).let(::hex)
    fun randomText(length: Int): String { val chars = "abcdefghijklmnopqrstuvwxyz0123456789"; return (0 until length).map { chars[random.nextInt(chars.length)] }.joinToString("") }
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    fun unhex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun md5(value: String) = hex(MessageDigest.getInstance("MD5").digest(value.toByteArray()))
    fun b64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    fun unb64(value: String): ByteArray = Base64.decode(value, Base64.DEFAULT)
    fun aes(data: ByteArray, key: String, iv: String? = null, decrypt: Boolean = false): ByteArray {
        val cipher = Cipher.getInstance(if (iv == null) "AES/ECB/PKCS5Padding" else "AES/CBC/PKCS5Padding")
        val spec = SecretKeySpec(key.toByteArray(), "AES")
        val mode = if (decrypt) Cipher.DECRYPT_MODE else Cipher.ENCRYPT_MODE
        if (iv == null) cipher.init(mode, spec) else cipher.init(mode, spec, IvParameterSpec(iv.toByteArray()))
        return cipher.doFinal(data)
    }
    fun rsa(data: ByteArray, key: String, pkcs: Boolean = false, padEnd: Boolean = false): String {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(unb64(key))) as java.security.interfaces.RSAPublicKey
        if (!pkcs) {
            val size = (publicKey.modulus.bitLength() + 7) / 8
            require(data.size <= size)
            val input = if (padEnd) data.copyOf(size) else data
            return BigInteger(1, input).modPow(publicKey.publicExponent, publicKey.modulus).toString(16).padStart(size * 2, '0')
        }
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return hex(cipher.doFinal(data))
    }
    const val NETEASE_RSA = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
    const val KUGOU_RSA = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB"
    const val KUGOU_LITE_RSA = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB"
    fun weapi(json: String, secret: String = randomText(16)): FormBody {
        val first = b64(aes(json.toByteArray(), "0CoJUm6Qyw8W8jud", "0102030405060708"))
        return FormBody.Builder().add("params", b64(aes(first.toByteArray(), secret, "0102030405060708")))
            .add("encSecKey", rsa(secret.reversed().toByteArray(), NETEASE_RSA)).build()
    }
    fun eapi(path: String, json: String): FormBody {
        val digest = md5("nobody" + path + "use" + json + "md5forencrypt")
        val text = path + "-36cd479b6b5-" + json + "-36cd479b6b5-" + digest
        return FormBody.Builder().add("params", hex(aes(text.toByteArray(), "e82ckenh8dichen8")).uppercase()).build()
    }
}
