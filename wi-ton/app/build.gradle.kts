import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Le credenciais de local.properties (fora do git).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String, fallback: String = ""): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: fallback)

android {
    namespace = "com.movedados.witon"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.movedados.witon"
        minSdk = 26          // exigido pelo supabase-kt e pelo ARCore
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    // Assinatura de release: so e configurada se o keystore existir.
    // No CI ele e materializado a partir do secret WITON_KEYSTORE_BASE64.
    // Local, sem keystore, o build de release sai sem assinatura em vez de quebrar.
    val keystorePath = secret("WITON_KEYSTORE_FILE")
    val hasKeystore = keystorePath.isNotBlank() && file(keystorePath).exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = secret("WITON_KEYSTORE_PASSWORD")
                keyAlias = secret("WITON_KEY_ALIAS")
                keyPassword = secret("WITON_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)

    // Realidade aumentada — ARCore + Filament via SceneView (Compose-native)
    implementation(libs.sceneview.ar)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
