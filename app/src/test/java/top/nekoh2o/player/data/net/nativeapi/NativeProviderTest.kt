package top.nekoh2o.player.data.net.nativeapi

import android.content.Context
import kotlinx.serialization.json.*
import okhttp3.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.nekoh2o.player.data.net.*
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class NativeProviderTest {
    @Test fun signaturesMatchUpstreamNodeReferenceVectors() {
        assertEquals("87ed129efe02710e214195f5821949aa", KugouNativeInterceptor.signature(mapOf("appid" to 1005,"mid" to "123","clienttime" to 1),"{}",false))
        assertEquals("47e76d0bfd8b74edd9a341c0a463922b", KugouNativeInterceptor.signature(mapOf("appid" to 3116,"mid" to "123","clienttime" to 1),"{}",true))
        assertEquals("4DC723619A991588865191FD2F319BAD79395D7A3F5CF04A611B5E255E942DF820000F1D08A6EA514EBCFADD6E0943F5FD307D598FFC698F466B5EC2A8D73C5802FAE2E1D4B836210CE6613616BB6E2F6162DF94AA9A5406E5F626D17CE711CD",
            Crypto.eapi("/api/test",obj("text" to "中文","n" to 1).toString()).value(0))
    }
    @Test fun eapiRoundTripIncludesPathAndIntegrityDigest() {
        val path="/api/test";val data="{\"text\":\"中文\",\"n\":1}"
        val params=Crypto.eapi(path,data).value(0)
        val decoded=String(Crypto.aes(Crypto.unhex(params),"e82ckenh8dichen8",decrypt=true))
        assertEquals(path+"-36cd479b6b5-"+data+"-36cd479b6b5-"+Crypto.md5("nobody"+path+"use"+data+"md5forencrypt"),decoded)
    }
    @Test fun weapiEncryptsUnicodeAndUsesRawRsa() {
        val data="{\"content\":\"评论中文\"}";val secret="abcdefghijklmnop"
        val form=Crypto.weapi(data,secret)
        val first=String(Crypto.aes(Crypto.unb64(form.value(0)),secret,"0102030405060708",true))
        assertEquals(data,String(Crypto.aes(Crypto.unb64(first),"0CoJUm6Qyw8W8jud","0102030405060708",true)))
        assertEquals(256,form.value(1).length)
        assertEquals(Crypto.rsa(secret.reversed().toByteArray(),Crypto.NETEASE_RSA),form.value(1))
    }
    @Test fun directNeteaseRemovesProxyCredentialsAndEncodesCommentBody() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("native_nc_test",Context.MODE_PRIVATE)
        val client=OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).addInterceptor(NeteaseNativeInterceptor(top.nekoh2o.player.data.net.AndroidProviderPreferences(prefs)))
            .addInterceptor { chain ->
                val request=chain.request()
                assertEquals("music.163.com",request.url.host)
                assertEquals("/weapi/resource/comments/add",request.url.encodedPath)
                assertNull(request.header("Authorization"));assertFalse(request.header("Cookie").orEmpty().contains("sp.sid"))
                assertFalse(request.url.toString().contains("评论"));assertTrue(request.body is FormBody)
                jsonResponse(request,obj("code" to 200))
            }.build()
        val form=FormBody.Builder().add("id","1").add("t","1").add("content","评论").build()
        client.newCall(Request.Builder().url("https://player.nekoh2o.top/api/comment").post(form)
            .header("Authorization","Bearer site-secret").header("Cookie","sp.sid=site-secret").build()).execute().use { assertTrue(it.isSuccessful) }
    }
    @Test fun directKugouUsesCorrectPlatformAndNeverSendsSiteCredentials() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("native_kg_test",Context.MODE_PRIVATE);prefs.edit().clear().commit()
        val sessions=KugouSessionStore(top.nekoh2o.player.data.net.AndroidProviderPreferences(prefs))
        val hosts=mutableListOf<String>()
        val client=OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).addInterceptor(KugouNativeInterceptor(sessions))
            .addInterceptor { chain ->
                val r=chain.request();hosts.add(r.url.host)
                assertEquals("https",r.url.scheme);assertNull(r.header("Authorization"));assertNull(r.header("Cookie"))
                assertEquals("3116",r.url.queryParameter("appid"));assertNotNull(r.url.queryParameter("signature"))
                jsonResponse(r,obj("status" to 1,"data" to obj("status" to 1)))
            }.build()
        client.newCall(Request.Builder().url("https://player.nekoh2o.top/kgapi/login/qr/check?platform=1&key=test")
            .header("Cookie","sp.sid=secret").header("Authorization","Bearer secret").build()).execute().close()
        assertEquals(listOf("login-user.kugou.com"),hosts)
        assertTrue(sessions.value(1,"KUGOU_API_MID").isNotBlank());assertEquals("",sessions.value(0,"KUGOU_API_MID"))
    }
    @Test fun kugouNestedRepliesKeepThreadAndParentSeparate() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("native_kg_reply",Context.MODE_PRIVATE)
        prefs.edit().clear().commit();val sessions=KugouSessionStore(top.nekoh2o.player.data.net.AndroidProviderPreferences(prefs))
        val client=OkHttpClient.Builder().addInterceptor(KugouNativeInterceptor(sessions)).addInterceptor { chain ->
            val r=chain.request();assertEquals("commentsv2/reply",r.url.queryParameter("r"))
            assertEquals("111",r.url.queryParameter("tid"));assertEquals("222",r.url.queryParameter("pid"));assertEquals("0",r.url.queryParameter("is_t"))
            assertEquals("回复中文",r.url.queryParameter("content"));assertNull(r.url.queryParameter("signature"))
            assertEquals("m.comment.service.kugou.com",r.header("x-router"));assertEquals("POST",r.method)
            jsonResponse(r,obj("status" to 1))
        }.build()
        val form=FormBody.Builder().add("tid","111").add("pid","222").add("is_t","0").add("content","回复中文").add("special_id","s").build()
        client.newCall(Request.Builder().url("https://native.invalid/kgapi/comment/floor/send?platform=0").post(form).build()).execute().close()
    }
    @Test fun smsAndTokenLoginUseCertificateValidHostForBothPlatforms() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("native_kg_login_host", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val sessions = KugouSessionStore(top.nekoh2o.player.data.net.AndroidProviderPreferences(prefs))
        for (platform in 0..1) {
            val client = OkHttpClient.Builder().addInterceptor(KugouNativeInterceptor(sessions))
                .addInterceptor { chain ->
                    val request = chain.request()
                    assertEquals("https", request.url.scheme)
                    assertEquals("login-user.kugou.com", request.url.host)
                    assertEquals(if (platform == 0) "1005" else "3116", request.url.queryParameter("appid"))
                    assertEquals("POST", request.method)
                    assertNull(request.header("Authorization"))
                    assertNull(request.header("Cookie"))
                    val buffer = okio.Buffer()
                    request.body!!.writeTo(buffer)
                    val body = buffer.readUtf8()
                    val params = request.url.queryParameterNames.filter { it != "signature" }
                        .associateWith { request.url.queryParameter(it) }
                    assertEquals(KugouNativeInterceptor.signature(params, body, platform == 1), request.url.queryParameter("signature"))
                    if (request.url.encodedPath == "/v7/send_mobile_code") {
                        val payload = Json.parseToJsonElement(body).jsonObject
                        assertEquals("invalid", payload.text("mobile"))
                        assertEquals("5", payload.text("businessid"))
                        assertNull(request.url.queryParameter("mobile"))
                    } else assertEquals("/v5/login_by_token", request.url.encodedPath)
                    jsonResponse(request, obj("status" to 0, "error_code" to 20010))
                }.build()
            for (endpoint in listOf("captcha/sent", "login/token")) {
                val request = Request.Builder().url("https://native.invalid/kgapi/" + endpoint + "?platform=" + platform)
                    .post(FormBody.Builder().add("mobile", "invalid").build()).build()
                client.newCall(request).execute().close()
            }
        }
    }

    @Test fun smsLiveTransportRejectsInvalidNumberWithoutSendingMessages() {
        org.junit.Assume.assumeTrue(System.getenv("KUGOU_LOGIN_SMOKE") == "1")
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("native_kg_sms_smoke", Context.MODE_PRIVATE)
        val client = OkHttpClient.Builder().followRedirects(false).callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(KugouNativeInterceptor(KugouSessionStore(top.nekoh2o.player.data.net.AndroidProviderPreferences(prefs)))).build()
        for (platform in 0..1) {
            val request = Request.Builder().url("https://native.invalid/kgapi/captcha/sent?platform=" + platform)
                .post(FormBody.Builder().add("mobile", "invalid").build()).build()
            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                val body = Json.parseToJsonElement(response.body!!.string()).jsonObject
                assertEquals("0", body.text("status"))
                assertEquals(20010L, body.number("error_code"))
            }
        }
    }
    @Test fun publicKugouSearchOmitsCredentialsAndPreservesChineseKeywords() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("native_kg_search",Context.MODE_PRIVATE)
        prefs.edit().clear().commit();val sessions=KugouSessionStore(top.nekoh2o.player.data.net.AndroidProviderPreferences(prefs))
        sessions.merge(1,mapOf("userid" to "123","token" to "private-token","dfid" to "private-device"))
        val client=OkHttpClient.Builder().addInterceptor(KugouNativeInterceptor(sessions)).addInterceptor { chain ->
            val r=chain.request();assertEquals("songsearch.kugou.com",r.url.host)
            assertEquals("/song_search_v2",r.url.encodedPath);assertEquals("梁博",r.url.queryParameter("keyword"))
            assertEquals("WebFilter",r.url.queryParameter("platform"));assertEquals("2",r.url.queryParameter("page"))
            for(name in listOf("token","userid")) { assertNull(r.url.queryParameter(name));assertNull(r.header(name)) }
            assertEquals("-",r.url.queryParameter("dfid"));assertEquals("1014",r.url.queryParameter("appid"))
            assertEquals(r.url.queryParameter("clienttime"),r.url.queryParameter("mid"));assertNotNull(r.url.queryParameter("signature"))
            assertFalse(r.url.toString().contains("private-"))
            assertNull(r.header("Cookie"));assertNull(r.header("Authorization"))
            jsonResponse(r,obj("status" to 1,"error_code" to 0,"data" to obj("lists" to emptyList<Any>())))
        }.build()
        client.newCall(Request.Builder().url("https://native.invalid/kgapi/search?platform=1&keywords=梁博&page=2")
            .header("Authorization","Bearer site-secret").build()).execute().close()
    }
    @Test fun nativeProviderSafeLiveSmoke() {
        org.junit.Assume.assumeTrue(System.getenv("NATIVE_API_SMOKE")=="1")
        val context=RuntimeEnvironment.getApplication()
        val nc=OkHttpClient.Builder().callTimeout(30,TimeUnit.SECONDS)
            .addInterceptor(NeteaseNativeInterceptor(top.nekoh2o.player.data.net.AndroidProviderPreferences(context.getSharedPreferences("native_nc_smoke",Context.MODE_PRIVATE)))).build()
        fun read(client: OkHttpClient, path: String): JsonObject = client.newCall(Request.Builder().url("https://native.invalid/"+path).build()).execute().use {
            assertTrue("HTTP "+it.code,it.isSuccessful);Json.parseToJsonElement(it.body!!.string()).jsonObject
        }
        for(endpoint in listOf("song/music/detail?id=186016","song/detail?ids=186016","login/qr/key","comment/music?id=186016&limit=1","search?keywords=周杰伦&limit=1","lyric/new?id=186016","song/url/v1?id=186016&level=standard","song/download/url/v1?id=186016&level=standard","register/anonimous")) {
            val result=read(nc,"api/"+endpoint)
            if(endpoint=="register/anonimous" && result.text("code")=="400") {
                println("NETEASE_UPSTREAM_LIMIT register/anonimous code=400 (also reproduced with upstream Node module)")
            } else { assertEquals(endpoint,"200",result.text("code"));println("NETEASE_OK "+endpoint.substringBefore('?')) }
        }
        val sessions=KugouSessionStore(top.nekoh2o.player.data.net.AndroidProviderPreferences(context.getSharedPreferences("native_kg_smoke",Context.MODE_PRIVATE)))
        val kg=OkHttpClient.Builder().callTimeout(30,TimeUnit.SECONDS).addInterceptor(KugouInterceptor(sessions)).addInterceptor(KugouNativeInterceptor(sessions)).build()
        for(platform in 0..1) {
            for(endpoint in listOf("register/dev","login/qr/key","search?keywords=梁博&pagesize=1","privilege/lite?hash=043C4DA61870CD55C1240F0FA6744C94","comment/music?mixsongid=302362878&pagesize=1","privilege/lite?hash=043C4DA61870CD55C1240F0FA6744C94&behavior=download","song/url?hash=043C4DA61870CD55C1240F0FA6744C94&quality=128&behavior=download")) {
                val result=read(kg,"kgapi/"+endpoint+(if('?' in endpoint) "&" else "?")+"platform="+platform)
                if(endpoint.startsWith("song/url")) {
                    println("KUGOU_URL_SUMMARY "+platform+" "+obj("status" to result["status"],"error" to result["error_code"],"quality" to result["quality"],"is_free_part" to result["is_free_part"],"fileSize" to result["fileSize"]))
                } else { assertEquals(endpoint,"1",result.text("status"));assertEquals(endpoint,0L,result.number("error_code","errcode","err_code"));println("KUGOU_OK "+platform+" "+endpoint.substringBefore('?')) }
                if(endpoint.startsWith("search?")) {
                    val tracks=result.obj("data").items("lists").ifEmpty { result.obj("data").items("info") }
                    assertTrue("Public search must return actual catalog rows",tracks.isNotEmpty())
                    val track=tracks.first()
                    val hash=track.text("FileHash","hash");assertTrue(hash.matches(Regex("[a-fA-F0-9]{32}")))
                    val query="hash="+hash+"&album_id="+track.text("AlbumID","album_id").ifBlank { "0" }+"&album_audio_id="+track.text("MixSongID","AlbumAudioID","album_audio_id").ifBlank { "0" }+"&quality=128&platform="+platform
                    for(behavior in listOf("play","download")) {
                        val url=read(kg,"kgapi/song/url?"+query+"&behavior="+behavior)
                        println("KUGOU_TRACK_ENTITLEMENT "+platform+" "+behavior+" "+obj("status" to url["status"],"error_code" to url["error_code"],"errcode" to url["errcode"],"error" to url["error"],"message" to url["message"],"urlCount" to (url["url"] as? JsonArray)?.size,"keys" to url.keys.toList()))
                        assertTrue("Unrecognized URL response",url.text("status") in listOf("0","1","2"))
                    }
                }
            }
        }
    }
}
