# Gson: preserva assinaturas genéricas para TypeToken funcionar no build de release
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.movedados.movetv.display.models.** { *; }
-keep class com.movedados.movetv.display.network.** { *; }
