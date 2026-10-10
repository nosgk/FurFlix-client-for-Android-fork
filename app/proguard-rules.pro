# Proguard rules for FurFlix

# 发布构建剥离调试日志(ISSUES S-001 / S-002)。
# Log.d / Log.v 是纯调试输出,且多处会打印完整 URL 与页面片段等敏感上下文
# (如 FurAffinityScraper 的 "Fetching: $url"、"Short HTML: ${html.take(500)}")。
# -assumenosideeffects 让 R8 在 release 中整条移除调用(含参数求值),
# 从而不泄漏内部信息;debug 构建不受影响,仍可正常排查。
# 保留 Log.w / Log.e:告警与错误级信息在生产中仍有诊断价值。
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# Keep JSoup
-keeppackagenames org.jsoup.nodes

# Keep data models
-keep class com.furflix.app.data.model.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class com.furflix.app.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.furflix.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Translation feature: kotlinx.serialization models live outside data.model
-keepclassmembers class com.furflix.app.translate.** {
    *** Companion;
}
-keepclasseswithmembers class com.furflix.app.translate.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.furflix.app.translate.**$$serializer { *; }
