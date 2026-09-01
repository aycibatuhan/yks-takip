# Anthropic Java SDK + Jackson use reflection heavily — keep them whole. Costs some
# APK size, buys correctness of the AI module under R8.
-keep class com.anthropic.** { *; }
-dontwarn com.anthropic.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-dontwarn java.beans.**
# The SDK's optional json-schema generator references reflect APIs absent on Android;
# those code paths (tool-schema generation) are never used by this app.
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn com.github.victools.**

# kotlinx.serialization — keep the generated serializers for the backup DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.yks2027.tracker.core.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.yks2027.tracker.core.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
