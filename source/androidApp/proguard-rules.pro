# ============================================================================
# Krill Android ProGuard/R8 Configuration - ULTRA CONSERVATIVE
# ============================================================================
# Strategy: KEEP EVERYTHING by default. Only obfuscate the specific krillapp
# packages that contain proprietary business logic.
#
# Goal: Protect IP in ONLY these packages:
#   - krill.zone.shared.krillapp.**
#
# ALL other packages remain completely unchanged to prevent any runtime issues.
# ============================================================================

# ============================================================================
# DISABLE SHRINKING AND OPTIMIZATION (Keep obfuscation only)
# ============================================================================
-dontoptimize
-dontshrink

# ============================================================================
# KEEP EVERYTHING BY DEFAULT - Then selectively obfuscate
# ============================================================================
# This keeps all classes but allows the krillapp packages to be obfuscated
-keep class !krill.zone.shared.krillapp.**,** { *; }

# ============================================================================
# OBFUSCATE ONLY THESE PACKAGES - Proprietary business logic
# ============================================================================
# These packages will have their class/method/field names obfuscated.
# The -keep,allowobfuscation rule means: keep the classes (don't shrink)
# but allow their names to be obfuscated.
-keep,allowobfuscation class krill.zone.shared.krillapp.** { *; }

# ============================================================================
# ANDROID-SPECIFIC KEEPS
# ============================================================================

# Keep Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable classes
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ============================================================================
# COMPOSE MULTIPLATFORM - CRITICAL
# ============================================================================
-keep class androidx.compose.** { *; }
-keep class **ComposableSingletons* { *; }
-keep class **$composable$* { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Compose stability inference
-keep class * implements androidx.compose.runtime.internal.StabilityInferred { *; }

# ============================================================================
# KOTLINX SERIALIZATION - CRITICAL
# ============================================================================

# Keep all @Serializable classes with their original names
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
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}

# ============================================================================
# KRILL ANNOTATIONS - Keep classes marked with @Krill
# ============================================================================
-keep @krill.zone.shared.ksp.Krill class ** { *; }
-keep @krill.zone.ksp.Krill class ** { *; }
-keep @interface krill.zone.shared.ksp.Krill
-keep @interface krill.zone.ksp.Krill

# ============================================================================
# KOIN DEPENDENCY INJECTION
# ============================================================================
-keep class org.koin.** { *; }
-keep class * extends org.koin.core.module.Module { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# ============================================================================
# KTOR CLIENT
# ============================================================================
-keep class io.ktor.** { *; }

# ============================================================================
# FIREBASE
# ============================================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ============================================================================
# KOTLIN REFLECTION AND COROUTINES
# ============================================================================
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keepclassmembers class kotlin.Metadata {
    *;
}

# ============================================================================
# GENERATED CODE
# ============================================================================
-keep class krill.zone.generated.** { *; }
-keep class krill.zone.ksp.** { *; }
-keep class krill.composeapp.generated.resources.** { *; }

# ============================================================================
# PRESERVE ATTRIBUTES
# ============================================================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault

# ============================================================================
# SUPPRESS WARNINGS
# ============================================================================
-ignorewarnings
-dontnote
-dontwarn **

