# NoctDock sender release safety.

# Manifest entry points and foreground service lifecycle.
-keep class com.glowseed.noctdock.sender.MainActivity { *; }
-keep class com.glowseed.noctdock.sender.ScreenCaptureService { *; }

# Kotlin serialization generated serializers used by local models and diagnostics exports.
-keep class com.glowseed.noctdock.**$$serializer { *; }
-keep class com.glowseed.noctdock.**$Companion { *; }
-keepclassmembers class com.glowseed.noctdock.** {
    public static ** Companion;
}

# Keep enum names stable for persisted DataStore settings and packet/config string mappings.
-keepclassmembers enum com.glowseed.noctdock.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# MediaProjection/MediaCodec classes are platform APIs; keep our wrapper names useful in diagnostics.
-keepnames class com.glowseed.noctdock.sender.MediaProjectionController
-keepnames class com.glowseed.noctdock.sender.H264ScreenEncoder
-keepnames class com.glowseed.noctdock.sender.InternalAudioSender
