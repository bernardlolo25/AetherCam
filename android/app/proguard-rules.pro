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
