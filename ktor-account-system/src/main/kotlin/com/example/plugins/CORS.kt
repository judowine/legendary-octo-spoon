package com.example.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCORS() {
    install(CORS) {
        // 許可するホスト
        allowHost("localhost:3000")
        allowHost("localhost:8080")

        // 許可するヘッダー
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)

        // 許可するHTTPメソッド
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)

        // クッキーを含むリクエストを許可
        allowCredentials = true

        // プリフライトリクエストのキャッシュ時間
        maxAgeInSeconds = 3600
    }
}
