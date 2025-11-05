package com.example.plugins

import com.example.routes.authRoutes
import com.example.routes.healthRoutes
import com.example.routes.userRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            // ヘルスチェック
            healthRoutes()

            // 認証関連
            authRoutes()

            // ユーザー管理
            userRoutes()
        }
    }
}
