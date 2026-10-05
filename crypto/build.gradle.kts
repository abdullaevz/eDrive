import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Saf Kotlin/JVM modulu: Android-dən asılı deyil, testləri adi JVM-də sürətlə işləyir.
// Gələcəkdə Spring Boot backend-də və ya Kotlin Multiplatform-da da istifadə oluna bilər.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.bouncycastle) // testlərdə Argon2id üçün (Android-də native argon2kt istifadə olunur)
}
