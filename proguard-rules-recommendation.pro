# Add these rules to your proguard-rules.pro file

# --- Room Database ---
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase$Builder { *; }
-keep class * extends androidx.room.Migration { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * implements androidx.room.TypeConverter { *; }

# Keep specific entities used in the app
-keep class it.vantaggi.scoreboardessential.database.** { *; }

# --- Wear OS Data Layer ---
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }
-keep class it.vantaggi.scoreboardessential.wear.WearDataLayerService { *; }
-keep class it.vantaggi.scoreboardessential.SimplifiedDataLayerListenerService { *; }

# Keep communication constants and data objects
-keep class it.vantaggi.scoreboardessential.shared.communication.WearConstants { *; }
-keep class it.vantaggi.scoreboardessential.shared.DataSyncObject { *; }

# --- Serialization / Parcelable ---
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Parcelable implementation in data classes
-keep class it.vantaggi.scoreboardessential.database.PlayerWithRoles { *; }

# --- Gson (Precautionary) ---
# If Gson is used transitively or explicitly
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# Prevent R8 from stripping the default constructor if used by Gson/Room via reflection
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- General ---
-keepattributes InnerClasses
-keepattributes EnclosingMethod
