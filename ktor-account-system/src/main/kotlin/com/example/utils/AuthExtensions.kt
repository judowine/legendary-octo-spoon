package com.example.utils

import com.example.plugins.UnauthorizedException
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * JWT認証済みユーザーのIDを取得
 *
 * @return ユーザーID
 * @throws UnauthorizedException 認証情報が無効な場合
 */
fun ApplicationCall.getUserId(): Long {
    val principal = principal<JWTPrincipal>()
        ?: throw UnauthorizedException("認証が必要です")

    val userId = principal.payload.subject?.toLongOrNull()
        ?: throw UnauthorizedException("無効なトークンです")

    return userId
}

/**
 * JWT認証済みユーザーのメールアドレスを取得
 *
 * @return メールアドレス
 * @throws UnauthorizedException 認証情報が無効な場合
 */
fun ApplicationCall.getUserEmail(): String {
    val principal = principal<JWTPrincipal>()
        ?: throw UnauthorizedException("認証が必要です")

    val email = principal.payload.getClaim("email").asString()
        ?: throw UnauthorizedException("無効なトークンです")

    return email
}
