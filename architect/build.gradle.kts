plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.intellij.platform") version "2.9.0" // Gradle plugin 2.x
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    // Целевая IDE
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        // если нужен Java PSI/инспекции:
        bundledPlugin("com.intellij.java")
    }

    // HTTP/JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.6.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // (Опционально) MCP клиент — подключайте позже, когда дойдём до MCP:
    // implementation("io.modelcontextprotocol:kotlin-sdk-client:<latest>")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks {
    // Запустить IDE с плагином
    runIde { }
}