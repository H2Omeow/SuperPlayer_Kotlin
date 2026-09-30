import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    kotlin("plugin.compose") version "2.0.20"
    id("org.jetbrains.compose") version "1.7.3"
}
repositories { google(); mavenCentral() }
version = "1.1.0-pre"
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }
tasks.withType<JavaCompile>().configureEach { sourceCompatibility = "1.8"; targetCompatibility = "1.8" }
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
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
        include("**/data/model/**", "**/data/net/*Api.kt", "**/data/net/AnimemusicApi.kt", "**/data/net/ProviderPreferences.kt")
        include("**/data/net/KugouSessionStore.kt", "**/data/net/KugouInterceptor.kt", "**/data/net/KugouResponse.kt", "**/data/net/KugouLoginError.kt")
        include("**/data/net/SourceGatewayInterceptor.kt", "**/ui/theme/Theme.kt")
        include("**/data/net/nativeapi/NativeSupport.kt", "**/data/net/nativeapi/*Interceptor.kt")
        include("**/data/repo/KugouRepository.kt", "**/data/repo/MusicRepository.kt", "**/data/repo/SongQualityRepository.kt", "**/data/repo/AnimemusicRepository.kt", "**/data/repo/CommentsRepository.kt", "**/data/repo/MvRepository.kt")
        include("**/lyric/**", "**/audio/NativeAudioEffectsController.kt")
    }
    into(layout.buildDirectory.dir("generated/shared"))
}
kotlin.sourceSets.main { kotlin.srcDir(sharedSources) }
tasks.processResources { from("../app/src/main/assets") { include("licenses/**") } }
tasks.withType<JavaExec>().configureEach { systemProperty("java.library.path", layout.buildDirectory.dir("native").get().asFile.absolutePath) }
tasks.test {
    systemProperty("java.library.path", layout.buildDirectory.dir("native").get().asFile.absolutePath)
    maxHeapSize = "256m"
}
val sharedTests by tasks.registering(Sync::class) {
    from("../app/src/test/java") { include("**/QualityAndCommentsTest.kt") }
    into(layout.buildDirectory.dir("generated/shared-tests"))
}
kotlin.sourceSets.test { kotlin.srcDir(sharedTests) }
tasks.test {
    dependsOn(tasks.named("buildNative"))
    systemProperty("java.library.path", layout.buildDirectory.dir("native").get().asFile.absolutePath)
}
tasks.register<Exec>("buildNative") {
    val output = layout.buildDirectory.dir("native").get().asFile
    outputs.dir(output)
    doFirst { output.mkdirs() }
    commandLine("cmake", "-S", "native", "-B", output.absolutePath, "-DCMAKE_BUILD_TYPE=Release")
    doLast { exec { commandLine("cmake", "--build", output.absolutePath, "-j1") } }
}

compose.desktop {
    application {
        mainClass = "top.nekoh2o.player.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "NekoPlayer"
            packageVersion = "1.1.0"
            description = "NekoPlayer music player"
            vendor = "H2Omeow"
            includeAllModules = true
            appResourcesRootDir.set(layout.projectDirectory.dir("build/installer-resources"))
            windows {
                shortcut = true
                menu = true
                menuGroup = "NekoPlayer"
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "25c7cfec-9f7e-4e52-bab9-df821af371cb"
            }
            linux {
                shortcut = true
                appCategory = "AudioVideo"
                debMaintainer = "H2Omeow"
            }
        }
    }
}
