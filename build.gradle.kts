// 根构建脚本：仅声明插件版本，不参与实际编译
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0 的 Compose 编译器插件，随 Kotlin 版本一起发布
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
