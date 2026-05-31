# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# ScoreboardEssential keep rules
# ---------------------------------------------------------------------------

# Keep Room entities, DAOs and relation/projection classes (accessed reflectively)
-keep class it.vantaggi.scoreboardessential.database.** { *; }
-keep interface it.vantaggi.scoreboardessential.database.** { *; }

# Keep data classes used for Wear communication and domain models
-keep class it.vantaggi.scoreboardessential.shared.** { *; }
-keep class it.vantaggi.scoreboardessential.domain.models.** { *; }
-keep class it.vantaggi.scoreboardessential.domain.model.** { *; }

# Keep Parcelable CREATOR fields
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Google Play Services Wearable
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.**

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep View/Data Binding generated classes
-keep class it.vantaggi.scoreboardessential.databinding.** { *; }

# WearableListenerService subclasses are instantiated by the framework
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }
