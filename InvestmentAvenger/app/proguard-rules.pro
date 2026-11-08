# kotlinx.serialization: keep the generated serializers of the saved data classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.portfoliopilot.**$$serializer { *; }
-keepclassmembers class com.example.portfoliopilot.** { *** Companion; }
-keepclasseswithmembers class com.example.portfoliopilot.** { kotlinx.serialization.KSerializer serializer(...); }

# WorkManager instantiates the worker by name.
-keep class com.example.portfoliopilot.DailyQuoteWorker { *; }
