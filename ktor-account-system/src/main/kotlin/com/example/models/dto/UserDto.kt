package com.example.models.dto

import kotlinx.serialization.Serializable

/**
 * ユーザー情報DTO
 */
@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val displayName: String?,
    val isEmailVerified: Boolean,
    val createdAt: String,
    val updatedAt: String? = null,
    val oauthAccounts: List<OAuthAccountDto>? = null
)

/**
 * OAuthアカウント情報DTO
 */
@Serializable
data class OAuthAccountDto(
    val provider: String,
    val connectedAt: String
)

/**
 * プロフィール更新リクエスト
 */
@Serializable
data class UpdateProfileRequest(
    val displayName: String?
)

/**
 * メールアドレス変更リクエスト
 */
@Serializable
data class ChangeEmailRequest(
    val newEmail: String,
    val password: String
)

/**
 * メールアドレス変更レスポンス
 */
@Serializable
data class ChangeEmailResponse(
    val message: String
)

/**
 * パスワード変更リクエスト
 */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

/**
 * パスワード変更レスポンス
 */
@Serializable
data class ChangePasswordResponse(
    val message: String
)

/**
 * アカウント削除リクエスト
 */
@Serializable
data class DeleteAccountRequest(
    val password: String? = null,
    val confirmation: String
)

/**
 * アカウント削除レスポンス
 */
@Serializable
data class DeleteAccountResponse(
    val message: String
)
