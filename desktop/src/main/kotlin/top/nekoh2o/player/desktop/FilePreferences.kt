package top.nekoh2o.player.desktop

import top.nekoh2o.player.data.net.ProviderPreferences
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties

class FilePreferences(private val file: Path) : ProviderPreferences {
    private val data = Properties()
    init { if (Files.exists(file)) Files.newInputStream(file).use { data.load(it) } }
    @Synchronized override fun getString(key: String, fallback: String?): String? = data.getProperty(key, fallback)
    @Synchronized override fun getBoolean(key: String, fallback: Boolean) = data.getProperty(key)?.toBoolean() ?: fallback
    override fun edit(): ProviderPreferences.Editor = object : ProviderPreferences.Editor {
        private val changes = linkedMapOf<String, String?>()
        override fun putString(key: String, value: String) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
        override fun remove(key: String) = apply { changes[key] = null }
        override fun apply() = synchronized(this@FilePreferences) {
            val next = Properties().apply { putAll(data) }
            changes.forEach { (key, value) -> if (value == null) next.remove(key) else next.setProperty(key, value) }
            Files.createDirectories(file.toAbsolutePath().parent)
            val temp = Files.createTempFile(file.toAbsolutePath().parent, ".prefs-", ".tmp")
            try {
                if (Files.getFileStore(temp).supportsFileAttributeView("posix"))
                    Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"))
                Files.newOutputStream(temp).use { next.store(it, "NekoPlayer preferences") }
                try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING) }
                data.clear(); data.putAll(next)
            } finally { Files.deleteIfExists(temp) }
        }
    }
}

object DesktopPaths {
    val home: Path = Paths.get(System.getProperty("nekoplayer.dataDir") ?: run {
        val base = if (System.getProperty("os.name").startsWith("Windows")) System.getenv("APPDATA")
            else System.getenv("XDG_CONFIG_HOME")
        (base?.takeIf { it.isNotBlank() } ?: (System.getProperty("user.home") + "/.config")) + "/NekoPlayer"
    })
    val preferences by lazy { FilePreferences(home.resolve("preferences.properties")) }
}
