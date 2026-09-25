package top.nekoh2o.player.data.net.nativeapi

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

internal object NativePlatform {
    val brand = "NekoPlayer"
    val device: String = System.getProperty("os.arch")
    val manufacturer = "Desktop"
    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unbase64(value: String): ByteArray = Base64.getMimeDecoder().decode(value)
    fun qrImage(url: String): String {
        val matrix = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 300, 300)
        val image = BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 300) for (x in 0 until 300) image.setRGB(x, y, if (matrix[x,y]) 0 else 0xffffff)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        return "data:image/png;base64," + base64(bytes)
    }
}
