package top.nekoh2o.player.data.repo

import top.nekoh2o.player.data.net.ApiFactory
import top.nekoh2o.player.data.net.AnimemusicSearchResponse
import top.nekoh2o.player.data.net.AnimemusicUrlResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface MvApi {
    @GET("mv/search")
    suspend fun search(@Query("source") source: String, @Query("keyword") keyword: String, @Query("page") page: Int): AnimemusicSearchResponse
    @GET("mv/url")
    suspend fun url(@Query("source") source: String, @Query("id") id: String): AnimemusicUrlResponse
}

data class MusicVideo(val id: String, val title: String, val artist: String,
    val artwork: String? = null, val source: String = "netease")

class MvRepository {
    suspend fun search(source: String, keyword: String, page: Int = 1): List<MusicVideo> {
        if (source.startsWith("animemusic-")) {
            val response = ApiFactory.animemusic.mvSearch(source.removePrefix("animemusic-"), keyword, page)
            return response.data.map { MusicVideo(it.id, it.title, it.artist, it.artwork, source) }
        }
        val response = ApiFactory.mv.search(source, keyword, page)
        return response.data.map { MusicVideo(it.id, it.title, it.artist, it.artwork, source) }
    }

    suspend fun url(video: MusicVideo): String? {
        if (video.source.startsWith("animemusic-")) {
            val platform = video.source.removePrefix("animemusic-")
            val quality = when (platform) { "wy", "tx" -> "1080"; "kw" -> "MP4BD"; else -> "" }
            return ApiFactory.animemusic.mvUrl(platform, video.id, quality).url?.takeIf(::httpUrl)
        }
        return ApiFactory.mv.url(video.source, video.id).url?.takeIf(::httpUrl)
    }

    private fun httpUrl(value: String) = value.startsWith("https://") || value.startsWith("http://")
}
