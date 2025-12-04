# Add project specific ProGuard rules here.
# By default, the Kotlin metadata is kept by ProGuard.
# See https://kotlinlang.org/docs/reference/android-overview.html#proguard
-keep class kotlin.Metadata { *; }

-dontwarn org.conscrypt.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.json.**
