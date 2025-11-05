package com.example.routes

import io.ktor.server.routing.*

fun Route.userRoutes() {
    route("/users") {
        // TODO: ユーザー管理エンドポイントの実装
        // - GET /me - プロフィール取得
        // - PATCH /me - プロフィール更新
        // - POST /me/email - メールアドレス変更
        // - POST /me/password - パスワード変更
        // - DELETE /me - アカウント削除
    }
}
