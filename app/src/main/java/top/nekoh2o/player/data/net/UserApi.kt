package top.nekoh2o.player.data.net

import top.nekoh2o.player.data.model.MeResp
import top.nekoh2o.player.data.model.UserData
import top.nekoh2o.player.data.model.UserDataResp
import top.nekoh2o.player.data.model.WallpaperResp
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Query

interface UserApi {
    @GET("auth/sso/me")
    suspend fun me(): MeResp

    @POST("auth/sso/logout")
    suspend fun logout(): retrofit2.Response<Unit>

    @GET("user/data")
    suspend fun pullData(): UserDataResp

    @PUT("user/data")
    suspend fun pushData(@Body data: UserData): UserDataResp

    @GET("local/wallpaper/list")
    suspend fun wallpaperList(@Query("orientation") orientation: String): WallpaperResp
}
