package top.nekoh2o.player.data.net

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class KugouSessionStoreTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("kg_cookie_management", Context.MODE_PRIVATE)
    private lateinit var sessions: KugouSessionStore
    @Before fun setup() {
        prefs.edit().clear().commit()
        sessions = KugouSessionStore(top.nekoh2o.player.data.net.AndroidProviderPreferences(prefs))
        sessions.merge(0, mapOf("token" to "old", "userid" to "1", "vip_token" to "old-vip", "dfid" to "device0", "KUGOU_API_MID" to "mid0"))
        sessions.merge(1, mapOf("token" to "lite", "userid" to "2", "dfid" to "device1"))
    }

    @Test fun importPersistsCredentialsAndRetainsDeviceWithoutLeakingOtherCookies() {
        sessions.importCookie(0, "Cookie: token=updated==; userid=123; Path=/; sp.sid=site-secret; MUSIC_U=netease-secret")
        val restored = KugouSessionStore(top.nekoh2o.player.data.net.AndroidProviderPreferences(prefs))
        assertEquals("updated==", restored.value(0, "token"))
        assertEquals("123", restored.value(0, "userid"))
        assertEquals("device0", restored.value(0, "dfid"))
        assertEquals("mid0", restored.value(0, "KUGOU_API_MID"))
        assertEquals("", restored.value(0, "vip_token"))
        assertFalse(restored.cookie(0).contains("secret"))
        assertEquals("lite", restored.value(1, "token"))
    }

    @Test fun importedDeviceAndVipFieldsStayInSelectedPlatform() {
        sessions.importCookie(1, "token=new-lite; userid=200; dfid=new-device; KUGOU_API_MID=new-mid; vip_token=new-vip")
        assertEquals("new-device", sessions.value(1, "dfid"))
        assertEquals("new-vip", sessions.value(1, "vip_token"))
        assertEquals("old-vip", sessions.value(0, "vip_token"))
        assertEquals("device0", sessions.value(0, "dfid"))
    }

    @Test fun invalidImportLeavesEntireSessionUntouched() {
        val before = sessions.cookie(0)
        for (invalid in listOf("", "token=only", "userid=123", "token=null; userid=1", "token=a; userid=0",
            "token=a; userid=-1", "token=a; userid=too-big", "token=a; userid=9999999999999999999999",
            "token=a; userid=1; token=b", "token=a; userid=1\r\nAuthorization: secret",
            "token=a b; userid=1", "token=中文; userid=1", "token=a; userid=1; malformed",
            "token=" + "x".repeat(16385) + "; userid=1")) {
            try { sessions.importCookie(0, invalid); fail("invalid Cookie accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(before, sessions.cookie(0))
        }
    }

    @Test fun clearRemovesSelectedDeviceAndLoginAndSurvivesRestart() {
        sessions.clearCookie(1)
        val restored = KugouSessionStore(top.nekoh2o.player.data.net.AndroidProviderPreferences(prefs))
        assertEquals("", restored.cookie(1))
        assertEquals("old", restored.value(0, "token"))
        assertEquals("device0", restored.value(0, "dfid"))
    }

    @Test fun transportMessagesDistinguishTlsDnsAndTimeoutWithoutEchoingSecrets() {
        val secret = "token=secret-phone-number"
        assertTrue(kugouLoginError(javax.net.ssl.SSLPeerUnverifiedException(secret)).contains("安全连接"))
        assertTrue(kugouLoginError(java.net.UnknownHostException(secret)).contains("DNS"))
        assertTrue(kugouLoginError(java.net.SocketTimeoutException(secret)).contains("超时"))
        assertFalse(kugouLoginError(java.io.IOException(secret)).contains(secret))
        assertEquals("短信请求过于频繁", kugouLoginError(KugouApiException("短信请求过于频繁")))
    }
}
