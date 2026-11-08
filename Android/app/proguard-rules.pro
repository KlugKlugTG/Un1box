# Keep JNI bridge methods called from Kotlin and any native-referenced callbacks.
-keep class org.un1box.android.Native { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
