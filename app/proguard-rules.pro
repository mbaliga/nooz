# No transmitting crash reporter, no analytics — nothing to keep for those.
# Keep Kotlinx Serialization metadata for @Serializable model/catalogue types.
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keep,includedescriptorclasses class xyz.mdhv.riverwip.**$$serializer { *; }
-keepclassmembers class xyz.mdhv.riverwip.** {
    *** Companion;
}
-keepclasseswithmembers class xyz.mdhv.riverwip.** {
    kotlinx.serialization.KSerializer serializer(...);
}
