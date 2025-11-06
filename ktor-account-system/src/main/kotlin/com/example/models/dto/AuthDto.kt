package com.example.models.dto

import kotlinx.serialization.Serializable

// ========================================
// 認証関連のリクエスト/レスポンス
// ========================================

/**
 * ユーザー登録リクエスト
 */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String? = null
)

/**
 * ユーザー登録レスポンス
 */
@Serializable
data class RegisterResponse(
    val user: UserDto,
    val message: String
)

/**
 * メール認証リクエスト
 */
@Serializable
data class VerifyEmailRequest(
    val token: String
)

/**
 * メール認証レスポンス
 */
@Serializable
data class VerifyEmailResponse(
    val message: String
)

/**
 * ログインリクエスト
 */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

/**
 * ログインレスポンス
 */
@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto
)

/**
 * トークン更新リクエスト
 */
@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

/**
 * トークン更新レスポンス
 */
@Serializable
data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

/**
 * ログアウトリクエスト
 */
@Serializable
data class LogoutRequest(
    val refreshToken: String
)

/**
 * ログアウトレスポンス
 */
@Serializable
data class LogoutResponse(
    val message: String
)

/**
 * パスワードリセット要求リクエスト
 */
@Serializable
data class PasswordResetRequest(
    val email: String
)

/**
 * パスワードリセット要求レスポンス
 */
@Serializable
data class PasswordResetResponse(
    val message: String
)

/**
 * パスワードリセット確認リクエスト
 */
@Serializable
data class PasswordResetConfirmRequest(
    val token: String,
    val newPassword: String
)

/**
 * パスワードリセット確認レスポンス
 */
@Serializable
data class PasswordResetConfirmResponse(
    val message: String
)
