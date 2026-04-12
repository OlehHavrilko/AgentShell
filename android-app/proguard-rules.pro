# Keep Kotlin metadata used by serialization/reflection-like flows
-keep class kotlin.Metadata { *; }

# Keep kotlinx.serialization generated serializers
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# Room entities/DAOs are referenced via generated code
-keep class com.agentshell.app.db.** { *; }

# Keep model classes used by JSON serialization in core module interactions
-keep class dev.agentshell.** { *; }
