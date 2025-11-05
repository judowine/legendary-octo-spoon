package com.example.routes

import io.ktor.server.routing.*

fun Route.authRoutes() {
    route("/auth") {
        // TODO: 認証エンドポイントの実装
        // - POST /register - ユーザー登録
        // - POST /login - ログイン
        // - POST /refresh - トークン更新
        // - POST /logout - ログアウト
        // - POST /verify-email - メール認証
        // - POST /password-reset/request - パスワードリセット要求
        // - POST /password-reset/confirm - パスワードリセット実行
        // - GET /oauth/{provider} - OAuth認証開始
        // - GET /oauth/{provider}/callback - OAuthコールバック
    }
}
