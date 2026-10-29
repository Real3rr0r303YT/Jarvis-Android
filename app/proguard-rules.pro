# Keep service classes referenced from the manifest by name.
-keep class com.real3rr0r303yt.jarvis.service.** { *; }

# Shizuku API uses reflection / AIDL; keep its surface intact.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
