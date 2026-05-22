# NoctDock receiver release safety.

# Manifest entry point.
-keep class com.glowseed.noctdock.receiver.MainActivity { *; }

# Kotlin serialization generated serializers used by local models and diagnostics exports.
-keep class com.glowseed.noctdock.**$$serializer { *; }
-keep class com.glowseed.noctdock.**$Companion { *; }
-keepclassmembers class com.glowseed.noctdock.** {
    public static ** Companion;
}

# Keep enum names stable for packet/config string mappings and persisted settings.
-keepclassmembers enum com.glowseed.noctdock.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep decoder/audio wrapper names useful in diagnostics.
-keepnames class com.glowseed.noctdock.receiver.H264VideoDecoder
-keepnames class com.glowseed.noctdock.receiver.PcmAudioPlayer
-keepnames class com.glowseed.noctdock.receiver.ReceiverSessionController
