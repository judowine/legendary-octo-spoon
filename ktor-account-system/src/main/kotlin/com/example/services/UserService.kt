package com.example.services

import com.example.models.dto.*
import com.example.plugins.ConflictException
import com.example.plugins.NotFoundException
import com.example.plugins.UnauthorizedException
import com.example.repositories.RefreshTokenRepository
import com.example.repositories.UserRepository
import com.example.security.PasswordHasher
import org.slf4j.LoggerFactory

/**
 * ユーザーサービス
 */
class UserService(
    private val userRepository: UserRepository = UserRepository(),
    private val refreshTokenRepository: RefreshTokenRepository = RefreshTokenRepository()
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    /**
     * プロフィール取得
     *
     * @param userId ユーザーID
     * @return ユーザー情報
     * @throws NotFoundException ユーザーが見つからない場合
     */
    fun getProfile(userId: Long): UserDto {
        logger.info("プロフィール取得: userId=$userId")

        val user = userRepository.findById(userId)
            ?: throw NotFoundException("ユーザーが見つかりません")

        return user.toDto()
    }

    /**
     * プロフィール更新
     *
     * @param userId ユーザーID
     * @param request 更新リクエスト
     * @return 更新されたユーザー情報
     * @throws NotFoundException ユーザーが見つからない場合
     */
    fun updateProfile(userId: Long, request: UpdateProfileRequest): UserDto {
        logger.info("プロフィール更新: userId=$userId")

        // ユーザーを更新
        val updatedUser = userRepository.update(
            id = userId,
            displayName = request.displayName
        ) ?: throw NotFoundException("ユーザーが見つかりません")

        logger.info("プロフィールを更新しました: userId=$userId")

        return updatedUser.toDto()
    }

    /**
     * メールアドレス変更
     *
     * @param userId ユーザーID
     * @param request メールアドレス変更リクエスト
     * @return 変更レスポンス
     * @throws NotFoundException ユーザーが見つからない場合
     * @throws UnauthorizedException パスワードが正しくない場合
     * @throws ConflictException メールアドレスが既に使用されている場合
     */
    fun changeEmail(userId: Long, request: ChangeEmailRequest): ChangeEmailResponse {
        logger.info("メールアドレス変更: userId=$userId, newEmail=${request.newEmail}")

        // ユーザーを検索
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("ユーザーが見つかりません")

        // パスワードが設定されていない（OAuth専用ユーザー）
        if (user.passwordHash == null) {
            throw UnauthorizedException("パスワードが設定されていません")
        }

        // パスワード検証
        if (!PasswordHasher.verifyPassword(request.password, user.passwordHash)) {
            logger.warn("メールアドレス変更失敗: パスワードが正しくありません - userId=$userId")
            throw UnauthorizedException("パスワードが正しくありません")
        }

        // 新しいメールアドレスの重複チェック
        if (userRepository.existsByEmail(request.newEmail)) {
            logger.warn("メールアドレス変更失敗: メールアドレスが既に使用されています - ${request.newEmail}")
            throw ConflictException("このメールアドレスは既に使用されています")
        }

        // メールアドレスを更新（is_email_verifiedをfalseに設定）
        userRepository.update(
            id = userId,
            email = request.newEmail,
            isEmailVerified = false
        )

        logger.info("メールアドレスを変更しました: userId=$userId, newEmail=${request.newEmail}")

        // TODO: 新しいメールアドレスに確認メールを送信

        return ChangeEmailResponse(
            message = "新しいメールアドレスに確認メールを送信しました"
        )
    }

    /**
     * パスワード変更
     *
     * @param userId ユーザーID
     * @param request パスワード変更リクエスト
     * @return 変更レスポンス
     * @throws NotFoundException ユーザーが見つからない場合
     * @throws UnauthorizedException 現在のパスワードが正しくない場合
     */
    fun changePassword(userId: Long, request: ChangePasswordRequest): ChangePasswordResponse {
        logger.info("パスワード変更: userId=$userId")

        // ユーザーを検索
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("ユーザーが見つかりません")

        // パスワードが設定されていない（OAuth専用ユーザー）
        if (user.passwordHash == null) {
            throw UnauthorizedException("パスワードが設定されていません")
        }

        // 現在のパスワード検証
        if (!PasswordHasher.verifyPassword(request.currentPassword, user.passwordHash)) {
            logger.warn("パスワード変更失敗: 現在のパスワードが正しくありません - userId=$userId")
            throw UnauthorizedException("現在のパスワードが正しくありません")
        }

        // 新しいパスワードをハッシュ化
        val newPasswordHash = PasswordHasher.hashPassword(request.newPassword)

        // パスワードを更新
        userRepository.updatePassword(userId, newPasswordHash)

        logger.info("パスワードを変更しました: userId=$userId")

        // セキュリティのため、全てのリフレッシュトークンを無効化
        refreshTokenRepository.revokeAllByUserId(userId)
        logger.info("全てのリフレッシュトークンを無効化しました: userId=$userId")

        return ChangePasswordResponse(
            message = "パスワードが変更されました。セキュリティのため、再度ログインしてください。"
        )
    }

    /**
     * アカウント削除
     *
     * @param userId ユーザーID
     * @param request アカウント削除リクエスト
     * @return 削除レスポンス
     * @throws NotFoundException ユーザーが見つからない場合
     * @throws UnauthorizedException パスワードが正しくない場合（パスワード認証の場合）
     */
    fun deleteAccount(userId: Long, request: DeleteAccountRequest): DeleteAccountResponse {
        logger.info("アカウント削除: userId=$userId")

        // ユーザーを検索
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("ユーザーが見つかりません")

        // パスワード認証ユーザーの場合はパスワード検証
        if (user.passwordHash != null && request.password != null) {
            if (!PasswordHasher.verifyPassword(request.password, user.passwordHash)) {
                logger.warn("アカウント削除失敗: パスワードが正しくありません - userId=$userId")
                throw UnauthorizedException("パスワードが正しくありません")
            }
        }

        // アカウントを削除（論理削除）
        userRepository.delete(userId)

        // 全てのリフレッシュトークンを無効化
        refreshTokenRepository.revokeAllByUserId(userId)

        logger.info("アカウントを削除しました: userId=$userId")

        return DeleteAccountResponse(
            message = "アカウントが削除されました"
        )
    }
}
