# Keep model classes
-keep class com.movedados.movetv.driver.models.** { *; }

# Keep Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson: preserva assinaturas genéricas para TypeToken funcionar no build de release
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# Mantém os modelos de dados intactos (evita a mesma quebra em qualquer classe usada com Gson)
-keep class com.movedados.movetv.driver.models.** { *; }
-keep class com.movedados.movetv.driver.network.** { *; }
