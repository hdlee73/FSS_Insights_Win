import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    id("org.jetbrains.compose") version "1.12.2"
}

group = "io.github.hdlee73"
version = "1.5.0"

// 뉴스 프록시 설정: CI 저장소 변수(환경변수) 또는 -P 로 넘긴 값을 앱 리소스로 심는다. 비어 있으면 '서버 미연결'.
val newsProxyUrl: String = (findProperty("newsProxyUrl") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("NEWS_PROXY_URL").orEmpty()
val newsProxyToken: String = (findProperty("newsProxyToken") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("NEWS_PROXY_TOKEN").orEmpty()

// 안드로이드 buildConfigField 대역을 만든다.
val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildConfig")
    val appVersion = version.toString()
    inputs.property("appVersion", appVersion)
    inputs.property("newsProxyUrl", newsProxyUrl)
    inputs.property("newsProxyToken", newsProxyToken)
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("build-config.properties").writeText(
            buildString {
                appendLine("APP_VERSION=$appVersion")
                appendLine("APP_VERSION_CODE=42")
                appendLine("BUILD_TYPE=release")
                appendLine("NEWS_PROXY_URL=$newsProxyUrl")
                appendLine("NEWS_PROXY_TOKEN=$newsProxyToken")
            }
        )
    }
}

sourceSets {
    main {
        resources.srcDir(generateBuildConfig)
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)
    // 안드로이드 앱과 같은 아이콘 모음을 쓴다(아이콘 정의는 Compose 버전과 무관하게 동작한다).
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("org.json:json:20260814")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

compose.desktop {
    application {
        mainClass = "io.github.hdlee73.financenewsradar.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "FSS Insights"
            packageVersion = "1.5.0"
            // MSI 요약 정보(WiX light)는 코드페이지 1252로 기록되므로 description·copyright 는 ASCII 로 둔다.
            description = "FSS Insights for Windows - financial news, market data and research search"
            copyright = "Copyright (c) 2026 hdlee73"
            vendor = "hdlee73"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "8E7B6A6B-2E3E-4B2E-9B5C-6A0F0E9B7A31"
            }

            modules("java.desktop", "java.net.http", "jdk.crypto.ec", "jdk.crypto.cryptoki", "jdk.unsupported")

            val icon = file("src/main/resources/app.ico")
            if (icon.exists()) {
                windows.iconFile.set(icon)
            }
        }
    }
}
