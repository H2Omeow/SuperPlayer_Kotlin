package top.nekoh2o.player.data.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginMetadataTest {
    @Test
    fun verifiesBothChecksumEncodingsBeforeRunningPlugins() {
        verifyPluginChecksum("abc", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        verifyPluginChecksum("abc", "sha256-ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsChecksumMismatch() {
        verifyPluginChecksum("modified", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun parsesPrettyPrintedMetadataWithoutExecutingScript() {
        val metadata = parsePluginMetadata("""
            const META = {
              "format": "animemusic/1",
              "name": "歌词 } 测试",
              "platform": "all",
              "platforms": ["kg", "wy"],
              "nested": {"value": "{escaped}"}
            };
            throw new Error('must not execute');
        """.trimIndent())!!
        assertEquals("animemusic/1", metadata.string("format"))
        assertEquals("歌词 } 测试", metadata.string("name"))
        assertEquals(listOf("kg", "wy"), metadata.strings("platforms"))
    }

    @Test
    fun rejectsMalformedOrNonJsonMetadata() {
        assertNull(parsePluginMetadata("module.exports={platform:'other'}"))
        assertNull(parsePluginMetadata("const META = {\"name\":\"unfinished\""))
        assertNull(parsePluginMetadata("let META = {name:'JavaScript object'};"))
    }

    @Test
    fun pluginRequestsRejectNonHttpAndEmbeddedCredentials() {
        assertTrue(validPluginUrl("https://example.test/plugin.js"))
        assertTrue(validPluginUrl("http://127.0.0.1:8000/audio"))
        assertFalse(validPluginUrl("file:///etc/passwd"))
        assertFalse(validPluginUrl("https://user:password@example.test/plugin.js"))
        assertFalse(validPluginUrl("https:///missing-host"))
    }
}
