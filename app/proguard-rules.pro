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

# onnxruntime-android (Nooz Cast) ships NO consumer proguard/R8 rules of its
# own — confirmed by inspecting the actual .aar, not assumed — unlike every
# other AAR dependency this app uses (Room/WorkManager/Coil/DataStore all
# bundle their own). Its Java classes are the JNI counterpart to the native
# libonnxruntime4j_jni.so, which looks method/field names up by exact name;
# R8 renaming or stripping anything here would silently break Nooz Cast only
# in release builds (debug never runs R8, so this gap was invisible until
# now — a real "worked in testing, broke in the Play build" trap).
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
