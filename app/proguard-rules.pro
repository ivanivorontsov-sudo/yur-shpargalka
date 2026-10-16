-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *** INSTANCE; }
-if class **$$serializer { *** INSTANCE; }
-keepclassmembers class org.ivanivorontsov.yur.** { *** Companion; }
-keepclassmembers class org.ivanivorontsov.yur.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class org.ivanivorontsov.yur.data.** { *; }
