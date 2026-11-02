# SafeNet Proguard & R8 Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}
