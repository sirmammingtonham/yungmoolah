# kotlinx.serialization keeps generated serializers via reflection on the companion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.yungmoolah.converter.** {
    *** Companion;
}
-keepclasseswithmembers class com.yungmoolah.converter.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.yungmoolah.converter.data.**$$serializer { *; }

# OkHttp ships optional Conscrypt/BouncyCastle/OpenJSSE hooks that aren't on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
