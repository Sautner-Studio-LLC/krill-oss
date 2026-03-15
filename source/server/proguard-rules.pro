# ============================================================================
# Krill Server ProGuard Configuration - ULTRA CONSERVATIVE
# ============================================================================
# Strategy: KEEP EVERYTHING by default. Only obfuscate the specific krillapp
# packages that contain proprietary business logic.
#
# Goal: Protect IP in ONLY these packages:
#   - krill.zone.server.krillapp.** 
#   - krill.zone.shared.krillapp.**
#
# ALL other packages remain completely unchanged to prevent any runtime issues.
# ============================================================================

# ============================================================================
# DISABLE ALL TRANSFORMATIONS EXCEPT OBFUSCATION
# ============================================================================
-dontoptimize
-dontshrink
-dontobfuscate

#-dontpreverify //never enable this - it breaks kotlin

# ============================================================================
# KEEP EVERYTHING BY DEFAULT
# ============================================================================
# This is the safest approach - keep all classes unchanged.
# Only the krillapp packages below will be obfuscated.
-keep class !krill.zone.server.krillapp.**,!krill.zone.shared.krillapp.**,** { *; }
# ============================================================================
# OBFUSCATE ONLY THESE PACKAGES - Proprietary business logic
# ============================================================================
# These packages will have their class/method/field names obfuscated.
# The -keep,allowobfuscation rule means: keep the classes (don't shrink)
# but allow their names to be obfuscated.
-keep,allowobfuscation class krill.zone.server.krillapp.** { *; }
-keep,allowobfuscation class krill.zone.shared.krillapp.** { *; }

# ============================================================================
# KEEP PACKAGE STRUCTURE
# ============================================================================
# Prevent package renaming/flattening
-keeppackagenames krill.zone.**
-flattenpackagehierarchy

# ============================================================================
# KEEP ALL ENUMS - Required for Enum.valueOf() to work at runtime
# ============================================================================
# Keep all enum class NAMES in krillapp packages (prevents class name obfuscation)
-keepnames enum krill.zone.server.krillapp.**
-keepnames enum krill.zone.shared.krillapp.**

# Keep enum constants and methods (values(), valueOf(), etc.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# Fully keep all enum classes in krillapp packages with original names
-keep enum krill.zone.server.krillapp.** { *; }
-keep enum krill.zone.shared.krillapp.** { *; }

# ============================================================================
# KEEP DATA CLASSES - Preserve their structure
# ============================================================================
# Keep data class names (classes with component functions are likely data classes)
-keepnames class krill.zone.server.krillapp.** {
    public ** component*();
}
-keepnames class krill.zone.shared.krillapp.** {
    public ** component*();
}

# Keep data class component functions and copy methods
-keepclassmembers class krill.zone.server.krillapp.** {
    public ** component*();
    public ** copy(...);
}
-keepclassmembers class krill.zone.shared.krillapp.** {
    public ** component*();
    public ** copy(...);
}

# ============================================================================
# EXCEPTIONS - Keep specific annotated classes even within krillapp
# ============================================================================

# Keep all @Serializable classes with their original names
# (Required for JSON serialization to work correctly)
-keep @kotlinx.serialization.Serializable class ** { *; }

-keep class org.jetbrains.exposed.** { *; }
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

# Keep @Krill annotated classes with their original names
-keep @krill.zone.shared.ksp.Krill class ** { *; }
-keep @krill.zone.ksp.Krill class ** { *; }
-keep @interface krill.zone.shared.ksp.Krill
-keep @interface krill.zone.ksp.Krill

# ============================================================================
# PRESERVE ATTRIBUTES
# ============================================================================
-keepattributes *Annotation*

# ============================================================================
# SUPPRESS WARNINGS
# ============================================================================
-ignorewarnings
-dontnote
-dontwarn **
