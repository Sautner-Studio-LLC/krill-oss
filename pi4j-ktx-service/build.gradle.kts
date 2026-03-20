// Root aggregator — subprojects carry all configuration
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.vanniktech) apply false
}
