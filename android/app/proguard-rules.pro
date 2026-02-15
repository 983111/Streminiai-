# ProGuard rules for Stremini AI
# Optimization flags
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Keep Flutter classes
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Android Components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View { public <init>(android.content.Context); }

# Keep Accessibility Services
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class Android.stremini_ai.ScreenReaderService { *; }
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService {
    public <init>();
}

# Keep Service classes
-keep class * extends android.app.Service { *; }
-keep class Android.stremini_ai.ChatOverlayService { *; }
-keep class Android.stremini_ai.StreminiIME { *; }

# Keep Activities
-keep class Android.stremini_ai.MainActivity { *; }
-keep class Android.stremini_ai.KeyboardSettingsActivity { *; }

# Keep Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * extends android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Network and JSON
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn java.nio.file.*
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class org.json.** { *; }

# Google Play Services
-keep class com.google.android.play.core.** { *; }
-keep class com.google.android.play.core.splitinstall.** { *; }
-keep class com.google.android.play.core.splitcompat.** { *; }
-keep class com.google.android.play.core.tasks.** { *; }

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# General Android keep rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
