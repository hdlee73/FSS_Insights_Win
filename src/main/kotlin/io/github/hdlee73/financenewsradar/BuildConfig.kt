package io.github.hdlee73.financenewsradar

import java.util.Properties

/**
 * 안드로이드 buildConfigField 대역. 버전은 안드로이드 앱과 맞춘다.
 * 뉴스 프록시 주소·토큰은 빌드할 때 `build-config.properties`로 앱에 함께 실어 넣고,
 * 파일이 없으면(예: 로컬 IDE 실행) 환경변수에서 읽는다. 둘 다 비어 있으면 '서버 미연결'로 동작한다.
 */
object BuildConfig {
    const val APPLICATION_ID = "io.github.hdlee73.financenewsradar"

    private val config: Properties = Properties().apply {
        runCatching {
            BuildConfig::class.java.getResourceAsStream("/build-config.properties")?.use { load(it) }
        }
    }

    private fun value(key: String, env: String): String =
        config.getProperty(key)?.takeIf { it.isNotBlank() }
            ?: System.getenv(env).orEmpty()

    val VERSION_NAME: String = config.getProperty("APP_VERSION")?.takeIf { it.isNotBlank() } ?: "2.0.2"
    val VERSION_CODE: Int = config.getProperty("APP_VERSION_CODE")?.toIntOrNull() ?: 55
    val BUILD_TYPE: String = config.getProperty("BUILD_TYPE")?.takeIf { it.isNotBlank() } ?: "release"

    val NEWS_PROXY_URL: String = value("NEWS_PROXY_URL", "NEWS_PROXY_URL")
    val NEWS_PROXY_TOKEN: String = value("NEWS_PROXY_TOKEN", "NEWS_PROXY_TOKEN")
}
