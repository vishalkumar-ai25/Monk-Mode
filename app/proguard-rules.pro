# Keep Room database and DAOs
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep our entities and DAOs
-keep class com.stayfocused.app.data.local.entities.** { *; }
-keep class com.stayfocused.app.data.local.dao.** { *; }
-keep class com.stayfocused.app.data.local.StayFocusedDatabase { *; }

# Keep anti-tampering and service components
-keep class com.stayfocused.app.service.** { *; }
-keep class com.stayfocused.app.receiver.** { *; }
-keep class com.stayfocused.app.vpn.** { *; }
