package top.nekoh2o.player.data.plugins

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest

@Serializable
data class PluginSubscription(
    val id: String,
    val name: String,
    val location: String,
    val builtIn: Boolean = false,
    val enabled: Boolean = true,
    val updatedAt: Long = 0
)

@Serializable
data class MusicPlugin(
    val id: String,
    val subscriptionId: String,
    val name: String,
    val location: String,
    val format: String,
    val descriptor: JsonObject,
    val script: String = "",
    val enabled: Boolean = true,
    val variables: JsonObject = JsonObject(emptyMap()),
    val storage: JsonObject = JsonObject(emptyMap())
) {
    val platform: String get() = descriptor.string("platform")
    val version: String get() = descriptor.string("version")
}

@Serializable
data class PluginState(
    val subscriptions: List<PluginSubscription> = emptyList(),
    val plugins: List<MusicPlugin> = emptyList(),
    val selectedSource: String = "netease"
)

data class PluginSource(val id: String, val name: String, val plugin: MusicPlugin, val platform: String)

internal fun JsonObject.string(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}.orEmpty()

internal fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray)
    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

internal fun pluginDigest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }

internal fun validPluginUrl(value: String): Boolean = runCatching {
    java.net.URI(value).let { it.scheme in listOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null }
}.getOrDefault(false)
