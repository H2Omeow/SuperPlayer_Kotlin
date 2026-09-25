package top.nekoh2o.player.data.net.nativeapi

import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.ByteArrayOutputStream

internal object NativePlatform {
    val brand: String get() = android.os.Build.BRAND
    val device: String get() = android.os.Build.DEVICE
    val manufacturer: String get() = android.os.Build.MANUFACTURER
    fun base64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    fun unbase64(value: String): ByteArray = Base64.decode(value, Base64.DEFAULT)
    fun qrImage(url: String): String {
        val matrix = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 320, 320)
        val pixels = IntArray(320 * 320) { i -> if (matrix[i % 320, i / 320]) 0xff000000.toInt() else -1 }
        val bitmap = Bitmap.createBitmap(pixels, 320, 320, Bitmap.Config.ARGB_8888)
        return try {
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            "data:image/png;base64," + base64(bytes.toByteArray())
        } finally { bitmap.recycle() }
    }
}
