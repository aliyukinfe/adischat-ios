# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepattributes Signature,*Annotation*
-keep class retrofit2.** { *; }
-keep interface com.adischat.app.network.** { *; }
-keep interface com.adischat.app.feature.shop.data.** { *; }
-keepclassmembers class com.adischat.app.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.adischat.app.data.model.** { *; }
-keep class com.adischat.app.network.** { *; }
-keep class com.adischat.app.feature.shop.data.** { *; }
-keepclassmembers class com.adischat.app.feature.shop.data.** { *; }
-keep class com.adischat.app.feature.shop.presentation.SellerVariant* { *; }
-keepclassmembers class com.adischat.app.feature.shop.presentation.SellerVariant* { *; }
-keep class com.adischat.app.feature.shop.presentation.CategoryHierarchy* { *; }
-keepclassmembers class com.adischat.app.feature.shop.presentation.CategoryHierarchy* { *; }
-keep class com.adischat.app.feature.restaurants.data.** { *; }
-keep interface com.adischat.app.feature.restaurants.data.** { *; }
-keep class com.adischat.app.feature.restaurants.center.** { *; }
