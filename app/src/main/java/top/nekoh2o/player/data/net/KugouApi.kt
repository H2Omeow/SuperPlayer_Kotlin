package top.nekoh2o.player.data.net

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/** The server selects a separate standard/lite process using platform=0/1. */
interface KugouApi {
    @GET("kgapi/{endpoint}")
    suspend fun get(
        @Path(value = "endpoint", encoded = true) endpoint: String,
        @Query("platform") platform: Int,
        @QueryMap parameters: Map<String, String> = emptyMap()
    ): Response<JsonObject>

    @FormUrlEncoded
    @POST("kgapi/{endpoint}")
    suspend fun post(
        @Path(value = "endpoint", encoded = true) endpoint: String,
        @Query("platform") platform: Int,
        @FieldMap parameters: Map<String, String>
    ): Response<JsonObject>
}
