// AGP 9 内置 Kotlin:不再声明 org.jetbrains.kotlin.android 插件。内置 Kotlin
// 默认绑定的 KGP 较旧,按官方迁移指南("Upgrade to a higher KGP version")显式
// 抬到 2.4.20,与下方 compose/serialization 子插件版本保持一致。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
