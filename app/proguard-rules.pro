# Shizzi release Proguard / R8 rules

# Keep Xray-core JNI/gomobile bridge classes
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep class com.v2ray.** { *; }

# Keep Shizuku AIDL + API surface
-keep class rikka.shizuku.** { *; }
-keep class com.shizzi.app.core.shizuku.** { *; }

# Keep Room generated code
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }

# Keep data models (parsed/serialized via reflection-free JSON)
-keep class com.shizzi.app.core.model.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
