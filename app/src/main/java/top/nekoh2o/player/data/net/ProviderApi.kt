package top.nekoh2o.player.data.net

import kotlinx.serialization.json.JsonObject
import retrofit2.http.*

interface ProviderApi {
    @GET("api/{endpoint}")
    suspend fun get(@Path(value = "endpoint", encoded = true) endpoint: String, @QueryMap parameters: Map<String, String> = emptyMap()): JsonObject
    @FormUrlEncoded @POST("api/{endpoint}")
    suspend fun post(@Path(value = "endpoint", encoded = true) endpoint: String, @FieldMap parameters: Map<String, String>): JsonObject
}
