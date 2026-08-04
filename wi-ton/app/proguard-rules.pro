# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.movedados.witon.data.remote.dto.** { *; }
-keep,includedescriptorclasses class com.movedados.witon.**$$serializer { *; }
-keepclassmembers class com.movedados.witon.** {
    *** Companion;
}
-keepclasseswithmembers class com.movedados.witon.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
