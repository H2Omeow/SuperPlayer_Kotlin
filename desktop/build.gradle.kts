plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    application
}
repositories { mavenCentral() }
version = "1.0.9-pre"
application { mainClass.set("top.nekoh2o.player.desktop.MainKt") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }
tasks.withType<JavaCompile>().configureEach { sourceCompatibility = "1.8"; targetCompatibility = "1.8" }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.formdev:flatlaf:3.7.2")
    testImplementation(kotlin("test-junit"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
val sharedSources by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include("**/data/model/**", "**/data/net/*Api.kt", "**/data/net/ProviderPreferences.kt")
        include("**/data/net/KugouSessionStore.kt", "**/data/net/KugouInterceptor.kt", "**/data/net/KugouResponse.kt", "**/data/net/KugouLoginError.kt")
        include("**/data/net/nativeapi/NativeSupport.kt", "**/data/net/nativeapi/*Interceptor.kt")
        include("**/data/repo/KugouRepository.kt", "**/data/repo/MusicRepository.kt", "**/data/repo/SongQualityRepository.kt", "**/data/repo/CommentsRepository.kt")
        include("**/lyric/**", "**/audio/NativeAudioEffectsController.kt")
    }
    into(layout.buildDirectory.dir("generated/shared"))
}
kotlin.sourceSets.main { kotlin.srcDir(sharedSources) }
tasks.processResources { from("../app/src/main/assets") { include("licenses/**") } }
tasks.withType<JavaExec>().configureEach { systemProperty("java.library.path", layout.buildDirectory.dir("native").get().asFile.absolutePath) }
tasks.test { systemProperty("java.library.path", layout.buildDirectory.dir("native").get().asFile.absolutePath) }
val sharedTests by tasks.registering(Sync::class) {
    from("../app/src/test/java") { include("**/QualityAndCommentsTest.kt") }
    into(layout.buildDirectory.dir("generated/shared-tests"))
}
kotlin.sourceSets.test { kotlin.srcDir(sharedTests) }
