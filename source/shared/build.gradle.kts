import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.targets.js.webpack.*


plugins {
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
}

// Mark WASM/JS tasks as incompatible with configuration cache due to Kotlin plugin limitations
// See: https://youtrack.jetbrains.com/issue/KT-50848
tasks.withType<KotlinWebpack>().configureEach {
    notCompatibleWithConfigurationCache("Kotlin/JS webpack tasks capture Project instances")
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
    notCompatibleWithConfigurationCache("Kotlin/JS test tasks capture Project instances")
}

kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.addAll("-opt-in=kotlin.ExperimentalUnsignedTypes,kotlin.RequiresOptIn", "-Xexpect-actual-classes")
                }
            }
        }
    }


    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Krill"
            isStatic = true
        }
    }

    jvm()


    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static(rootDirPath)
                    static(projectDirPath)
                }
            }
            testTask {
                enabled = false
            }

        }


    }

    androidLibrary {
        namespace = "krill.zone.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val commonMain  by getting {
            dependencies {
                api(libs.compose.runtime)
                api(libs.koin.annotations)
                implementation(libs.koin.core)
                implementation(libs.bundles.ktorClient)
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.datetime)
                implementation(libs.settings)
                implementation(libs.kermit)


            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.koin.test)
                implementation(libs.kotlin.coroutines.test)
            }
        }




        jvmMain {
            dependencies {

                implementation(libs.bundles.ktorClientJvm)
                implementation(libs.jlayer)


            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.mockk)
            }
        }
        wasmJsMain {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(libs.bundles.ktorClientJs)
            }
        }
        androidMain {

            dependsOn(commonMain)
            dependencies {
                implementation(libs.koin.android)
                // For Compose on Android
                implementation(libs.koin.compose)
                implementation(libs.bundles.media3)
                implementation(libs.bundles.ktorClientAndroid)

            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.koin.compose)
                implementation(libs.ktor.client.darwin)
            }
        }

        iosArm64Main {
            dependsOn(iosMain)
        }
        iosX64Main {
            dependsOn(iosMain)
        }
        iosSimulatorArm64Main {
            dependsOn(iosMain)
        }

    }
}

