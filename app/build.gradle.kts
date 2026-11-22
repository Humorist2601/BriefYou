import java.io.FileInputStream
import java.util.Properties

plugins {
    // Brief You: no Kotlin Android plugin: AGP 9 has Kotlin built in.
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

fun fetchGitCommitHash(): String {
    val process =
        ProcessBuilder("git", "rev-parse", "--verify", "--short", "HEAD")
            .redirectErrorStream(true)
            .start()
    return process.inputStream.bufferedReader().use { it.readText().trim() }
}

val gitCommitHash = fetchGitCommitHash()
val keyProps = Properties()
val releaseKeyPropsFile: File = rootProject.file("signature/keystore_release.properties")


if (releaseKeyPropsFile.exists()) {
    println("Loading keystore properties from ${releaseKeyPropsFile.absolutePath}")
    keyProps.load(FileInputStream(releaseKeyPropsFile))
}

android {
    compileSdk = 37 // Brief You: Compose 1.12 needs it

    defaultConfig {
        applicationId = "com.ay.briefyou"
        minSdk = 26
        targetSdk = 37 // Brief You: Android 17
        versionCode = 47
        versionName = "0.16.2"

        buildConfigField(
            "String",
            "USER_AGENT_STRING",
            "\"BriefYou/${versionName}(${versionCode})\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Brief You ships for 64-bit ARM phones only (smaller APK; LiteRT-LM targets arm64).
        ndk { abiFilters += listOf("arm64-v8a") }

        room { schemaDirectory("$projectDir/schemas") }

        ksp { arg("room.incremental", "true") }
    }

    flavorDimensions.add("channel")
    productFlavors {
        create("github") {
            isDefault = true
            dimension = "channel"
            // Brief You has its own app ID.
            applicationId = "com.ay.briefyou"
            // Brief You's own name in the network user agent.
            buildConfigField(
                "String",
                "USER_AGENT_STRING",
                "\"BriefYou/${defaultConfig.versionName}(${defaultConfig.versionCode})\"",
            )
            // CI builds get a rising version so each release installs as an update. Kept here,
            // in one place.
            System.getenv("CI_BUILD_NUMBER")?.toIntOrNull()?.let { build ->
                versionCode = build
                versionNameSuffix = ".$build"
            }
        }
    }
    signingConfigs {
        create("release") {
            keyAlias = keyProps["keyAlias"] as String?
            keyPassword = keyProps["keyPassword"] as String?
            storeFile = keyProps["storeFile"]?.let { file(it as String) }
            storePassword = keyProps["storePassword"] as String?
        }
    }
    lint { disable.addAll(listOf("MissingTranslation", "ExtraTranslation")) }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        // Brief You: test builds ("Brief You Nightly"), the same as release but with their own
        // app ID, name and icon (app/src/nightly), so they install next to Brief You.
        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-nightly"
            proguardFile("proguard-nightly.pro") // Brief You: readable names for the stutter log
            matchingFallbacks += listOf("release")
        }
        all { signingConfig = signingConfigs.getByName("release") }
    }
    // Brief You: the APK's name (BriefYou-<version>-<commit>.apk) is given by the build workflow:
    // AGP 9 removed the old variant API that named it here.
    // Brief You: Kotlin's options are set in kotlin { compilerOptions } below (kotlinOptions is
    // deprecated).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { buildConfig = true }
    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        resources.excludes.add("rome-utils-*.jar")
        // Brief You: a jar inside Rome's jar and the libraries' licence and change notes, none read
        // by the app (the licences are shown from AboutLibraries' own data).
        resources.excludes.add("/*.jar")
        resources.excludes.add("/META-INF/**/LICENSE.txt")
        resources.excludes.add("/META-INF/CHANGES")
        // Brief You: the on-device AI library (22 MB) is compressed in the APK (about 12 MB smaller
        // to download); Android unpacks it when the app is installed.
        jniLibs.useLegacyPackaging = true
    }
    androidResources {
        generateLocaleConfig = true
        // Brief You: English only (Brief You's own text is English; about 1 MB smaller).
        localeFilters += listOf("en")
    }
    namespace = "com.ay.briefyou"
}

aboutLibraries { export { excludeFields.add("generated") } } // Brief You: AboutLibraries 15

// Brief You: the compilerOptions DSL, in place of the deprecated kotlinOptions.
kotlin {
    compilerOptions {
        optIn.add("kotlin.RequiresOptIn")
        // LiteRT-LM is built with a newer Kotlin than this project.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // AboutLibraries
    implementation(libs.media3.exoplayer) // Brief You: the podcast and video player
    implementation(libs.media3.session) // Brief You
    implementation(libs.media3.ui.compose) // Brief You
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose)

    // Compose
    implementation(libs.compose.html)
    implementation(platform(libs.compose.bom.stable)) // Brief You: Compose 1.11.1 (Material 3 is pinned in libs.versions.toml)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.material.kolor) // Brief You
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    // Coil
    implementation(libs.coil.base)
    implementation(libs.coil.network) // Brief You: Coil 3 fetches through OkHttp with this
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)

    // Hilt
    implementation(libs.hilt.work)
    implementation(libs.hilt.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.viewmodel)

    // AndroidX
    implementation(libs.android.svg)
    implementation(libs.opml.parser)
    implementation(libs.readability4j)
    implementation(libs.rome)
    implementation(libs.rome.modules)
    implementation(libs.telephoto)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.profileinstaller)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.room.paging)
    implementation(libs.room.common)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.paging.common.ktx)
    implementation(libs.paging.runtime.ktx)
    implementation(libs.paging.compose)
    implementation(libs.browser)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.appwidget.preview)
    implementation(libs.glance.material3)
    implementation(libs.glance.preview)

    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    //    implementation(libs.compose.material3.adaptive.navigation3)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigationevent)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.material3.adaptive.layout)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.timber)

    // On-device AI summaries (LiteRT-LM + Gemma) // Brief You
    implementation(libs.litertlm.android)

}
