# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Room database implementations, DAOs, and Entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class **.*_Impl { *; }

# Keep app data models, ViewModels, NLP and Audio/TTS helpers
-keep class com.example.data.** { *; }
-keep class com.example.nlp.** { *; }
-keep class com.example.audio.** { *; }

# Keep JSON classes
-keep class org.json.** { *; }

# Keep WebView JavascriptInterface methods and annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

