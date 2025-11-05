package com.example

import com.example.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    // データベース設定
    configureDatabases()

    // セキュリティ設定（JWT認証）
    configureSecurity()

    // シリアライゼーション設定
    configureSerialization()

    // HTTPルーティング設定
    configureRouting()

    // エラーハンドリング設定
    configureStatusPages()

    // CORS設定
    configureCORS()

    // ロギング設定
    configureLogging()
}
