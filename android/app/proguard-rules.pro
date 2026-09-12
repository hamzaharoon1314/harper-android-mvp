-keep class uniffi.harper_android.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
    <methods>;
}

-dontwarn java.awt.**
-dontwarn com.sun.jna.**
