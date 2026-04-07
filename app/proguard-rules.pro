# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.marcelo.protocol.**$$serializer { *; }
-keepclassmembers class com.marcelo.protocol.** {
    *** Companion;
}
-keepclasseswithmembers class com.marcelo.protocol.** {
    kotlinx.serialization.KSerializer serializer(...);
}
