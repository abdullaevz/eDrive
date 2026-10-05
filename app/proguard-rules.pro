# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.edrive.** { kotlinx.serialization.KSerializer serializer(...); }
# argon2kt (JNI)
-keep class com.lambdapioneer.argon2kt.** { *; }
