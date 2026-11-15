# AetherCam ProGuard / R8 Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep MediaCodec & Camera2 models
-keep class com.aethercam.app.camera.** { *; }
-keep class com.aethercam.app.network.** { *; }
-keep class com.aethercam.app.security.** { *; }

# Keep ZXing QR classes
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep Compose Runtime
-keep class androidx.compose.** { *; }

# Suppress warnings for missing annotations in third-party security libraries
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
