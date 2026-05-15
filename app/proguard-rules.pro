# Data models (Room entities / serialized classes)
-keep class dev.goor.tv.data.model.** { *; }
-keepattributes *Annotation*

# CastOptionsProvider is loaded by class name from manifest meta-data
-keep class dev.goor.tv.cast.CastOptionsProvider { *; }

# ViewModels are instantiated by Koin via reflection
-keep class dev.goor.tv.ui.screens.**.** extends androidx.lifecycle.ViewModel { *; }

# Google Cast SDK
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.cast.**
-dontwarn com.google.android.gms.cast.framework.**

# MediaRouter
-keep class androidx.mediarouter.** { *; }
-dontwarn androidx.mediarouter.**
