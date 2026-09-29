# Keep SCOS2 Application, Services, Receivers & Components
-keep class com.awork.camera6.** { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }

# Keep Dagger / Hilt
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class androidx.hilt.** { *; }

# Keep Coroutines & Serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { *; }

