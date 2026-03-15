import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.googleServices)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    jvmToolchain(21)
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }

}


android {
    namespace = "krill.zone"
    compileSdk = libs.versions.android.compileSdk.get().toInt()


    defaultConfig {
        applicationId = "krill.zone"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = 36
        versionCode = 559
        versionName = "1.0.756"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        release {
            isMinifyEnabled = true
            isShrinkResources = false  // Disabled - only obfuscation, no shrinking
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,MANIFEST.MF}"
        }
    }

    buildFeatures {
        compose = true
    }


}

dependencies {
    api(libs.compose.ui.tooling)
    ksp(projects.ksp)
    implementation(projects.shared)
    implementation(projects.generated)
    implementation(projects.composeApp)
    implementation(platform(libs.firebase))
    implementation(libs.firebase.analytics)
    implementation(libs.koin.android)
    implementation(libs.bundles.media3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.android)
    implementation(libs.bundles.androidCompose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.koin.android)
    implementation(platform(libs.koin.bom))
    testImplementation(libs.bundles.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("project", project.name)
    arg("project-root", "${project.rootDir}")
}


