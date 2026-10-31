# Add project specific ProGuard rules here.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault

# Keep Firestore and data model classes serialized via reflection
-keep class com.example.data.model.** { *; }
-keep class com.example.domain.** { *; }

# Keep OkHttp / Okio warnings quiet
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep org.json classes
-keep class org.json.** { *; }
