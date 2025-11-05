plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

group = "com.example"
version = "1.0.0"

application {
    mainClass.set("com.example.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server（バンドル使用）
    implementation(libs.bundles.ktor.server)

    // Ktor Client（バンドル使用）
    implementation(libs.bundles.ktor.client)

    // Serialization（バンドル使用）
    implementation(libs.bundles.serialization)

    // Database（バンドル使用）
    implementation(libs.bundles.database)

    // Security（バンドル使用）
    implementation(libs.bundles.security)

    // Redis
    implementation(libs.lettuce.core)

    // Validation
    implementation(libs.konform)

    // Utilities
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback.classic)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.bundles.testing.kotest)
    testImplementation(libs.bundles.testing.containers)
    testImplementation(libs.mockk)
}

tasks {
    test {
        useJUnitPlatform()
    }

    // Dockerイメージビルド用タスク
    register("buildDockerImage") {
        dependsOn("build")
        doLast {
            exec {
                commandLine("docker", "build", "-t", "ktor-account-system:latest", "-f", "docker/Dockerfile", ".")
            }
        }
    }
}

// Kotlin設定
kotlin {
    jvmToolchain(17)
}
