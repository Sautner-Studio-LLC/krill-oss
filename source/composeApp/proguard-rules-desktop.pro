# ============================================================================
# Krill Desktop ProGuard Configuration - EXTREMELY CONSERVATIVE
# ============================================================================
# Strategy: Keep EVERYTHING intact to avoid Compose/KMP issues.
# ONLY obfuscate: non-annotated classes in krill.zone.shared.**
#
# Goal: Minimal obfuscation to protect some IP without breaking Compose
# ============================================================================

# ============================================================================
# DISABLE ALL TRANSFORMATIONS
# ============================================================================
-dontoptimize
-dontshrink

# ============================================================================
# Suppress all warnings
# ============================================================================
-ignorewarnings
-dontwarn **
-dontnote

# ============================================================================
# KEEP EVERYTHING - Blanket protection for Compose/KMP
# ============================================================================
# Keep all Kotlin and framework classes
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class org.jetbrains.** { *; }
-keep class korlibs.** { *; }

# Keep all Compose classes (critical for UI)
-keep class androidx.compose.** { *; }
-keep class **ComposableSingletons* { *; }
-keep class **$composable$* { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep all Ktor classes
-keep class io.ktor.** { *; }

# Keep all Koin DI classes
-keep class org.koin.** { *; }

# Keep all logging classes
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }
-keep class co.touchlab.kermit.** { *; }
-keep class androidx.collection.** { *; }
# Keep all krill.zone.app classes (Desktop UI code)
-keep class krill.zone.app.** { *; }
-keep class krill.zone.MainKt { *; }
-keep class krill.zone.main.** { *; }

# Keep all krill.zone.di classes (Dependency Injection)
-keep class krill.zone.di.** { *; }
-keep class krill.zone.shared.di.** { *; }

# Keep generated resources and KSP code
-keep class krill.composeapp.generated.resources.** { *; }
-keep class krill.zone.ksp.** { *; }
-keep class krill.zone.generated.** { *; }

# ============================================================================
# Kotlinx Serialization - CRITICAL for @Serializable classes
# ============================================================================
# Keep all classes annotated with @Serializable (must override any obfuscation)
-keep @kotlinx.serialization.Serializable class ** { *; }

# Keep all generated serializers
-keep class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclassmembers class **$$serializer {
    *;
}

# Keep serializer() method in companion objects
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all KSerializer implementations
-keep class * implements kotlinx.serialization.KSerializer { *; }

# Keep serialization descriptors
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    private static ** Companion;
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer();
    kotlinx.serialization.descriptors.SerialDescriptor getDescriptor();
}
-keep class !krill.zone.server.krillapp.**,!krill.zone.shared.krillapp.**,** { *; }
# ============================================================================
# @Krill Annotation - Keep classes marked with custom Krill annotation
# ============================================================================
# Keep all classes annotated with @Krill
-keep @krill.zone.shared.ksp.Krill class ** { *; }
-keep @krill.zone.ksp.Krill class ** { *; }

# Keep the annotation itself
-keep @interface krill.zone.shared.ksp.Krill
-keep @interface krill.zone.ksp.Krill

# ============================================================================
# OBFUSCATE TARGET - krill.zone.shared (non-annotated classes only)
# ============================================================================
# Allow obfuscation of non-annotated classes in shared package
# The @Serializable and @Krill rules above will override this for annotated classes
-keep,allowobfuscation class krill.zone.shared.krillapp.** { *; }
-keep,allowobfuscation class krill.zone.server.krillapp.** { *; }
# ============================================================================
# Keep attributes for reflection and debugging
# ============================================================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses


# ============================================================================
# Keep important attributes
# ============================================================================
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations

# ============================================================================
# Resource handling
# ============================================================================
-dontwarn sun.util.resources.**
-adaptresourcefilecontents META-INF/services/**
