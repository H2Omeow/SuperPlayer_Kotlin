-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class top.nekoh2o.player.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class top.nekoh2o.player.data.model.**$$serializer { *; }

# ===== 数据模型：R8 会重命名字段，破坏 kotlinx.serialization 的 JSON 映射 =====
-keep class top.nekoh2o.player.data.model.** { *; }

# ===== Retrofit：保留接口方法上的注解与泛型签名，否则请求会失败 =====
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keepclasseswithmembers interface top.nekoh2o.player.data.net.** { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ===== kotlinx.serialization 运行时 =====
-keepclassmembers class kotlinx.serialization.json.** { *; }
-dontwarn kotlinx.serialization.**
