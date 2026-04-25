# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Keep retrofit interfaces themselves so the proxy can be created. R8 full-mode
# otherwise strips the interface even though its methods are kept.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
# Keep generic signatures on Call / Response so suspend fun resolution finds the
# wrapped type — without these you get "Class cannot be cast to ParameterizedType"
# from retrofit when introspecting the suspend function's return type.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
# Required by Retrofit's official ProGuard recipe — suspend fun support uses
# kotlin.coroutines.Continuation reflectively, and R8 full-mode will otherwise
# rewrite the Continuation parameter such that Retrofit's generic-type extraction
# returns a raw Class instead of a ParameterizedType.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Explicit keep on our Retrofit interface — R8 full-mode is aggressive about
# proxying interfaces and the @retrofit2.http.* match alone doesn't reliably
# preserve suspend-fun generics on the Continuation parameter.
-keep interface com.charles.ollama.client.data.api.OllamaApi { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Honor @SerializedName on any model class regardless of whether the class
# itself is otherwise kept.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Ollama API DTOs — Gson reflects against these to deserialize /api/tags etc.
# Without these keeps, R8 strips the List<ModelInfo> generic signature and the
# response decode fails with "java.lang.Class cannot be cast to
# java.lang.reflect.ParameterizedType".
-keep class com.charles.ollama.client.data.api.dto.** { *; }
-keep class com.charles.ollama.client.domain.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Firebase Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }
-keep class com.google.android.gms.measurement.** { *; }
-keep class com.google.android.gms.internal.measurement.** { *; }
-dontwarn com.google.firebase.analytics.**
-dontwarn com.google.android.gms.measurement.**

# Firebase Performance Monitoring
-keep class com.google.firebase.perf.** { *; }
-dontwarn com.google.firebase.perf.**

# Firebase Messaging
-keep class com.google.firebase.messaging.** { *; }
-dontwarn com.google.firebase.messaging.**

# Firebase Config
-keep class com.google.firebase.remoteconfig.** { *; }
-dontwarn com.google.firebase.remoteconfig.**

# AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# LiteRT-LM (JNI / native)
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

