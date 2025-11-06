package com.example.services

import com.example.models.dto.*
import com.example.models.entities.User
import com.example.plugins.ConflictException
import com.example.plugins.ForbiddenException
import com.example.plugins.NotFoundException
import com.example.plugins.UnauthorizedException
import com.example.repositories.EmailVerificationRepository
import com.example.repositories.PasswordResetRepository
import com.example.repositories.RefreshTokenRepository
import com.example.repositories.UserRepository
import com.example.security.JwtConfig
import com.example.security.PasswordHasher
import com.example.security.TokenGenerator
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 認証サービス
 */
class AuthService(
    private val userRepository: UserRepository = UserRepository(),
    private val emailVerificationRepository: EmailVerificationRepository = EmailVerificationRepository(),
    private val passwordResetRepository: PasswordResetRepository = PasswordResetRepository(),
    private val refreshTokenRepository: RefreshTokenRepository = RefreshTokenRepository(),
    private val emailService: EmailService = EmailService(),
    private val tokenService: TokenService = TokenService()
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    /**
     * ユーザー登録
     *
     * @param request 登録リクエスト
     * @return 登録レスポンス
     * @throws ConflictException メールアドレスが既に登録されている場合
     */
    fun register(request: RegisterRequest): RegisterResponse {
        logger.info("ユーザー登録を開始: ${request.email}")

        // メールアドレスの重複チェック
        if (userRepository.existsByEmail(request.email)) {
            logger.warn("メールアドレスが既に登録されています: ${request.email}")
            throw ConflictException("このメールアドレスは既に登録されています")
        }

        // パスワードをハッシュ化
        val passwordHash = PasswordHasher.hashPassword(request.password)

        // ユーザーを作成
        val user = userRepository.create(
            email = request.email,
            passwordHash = passwordHash,
            displayName = request.displayName,
            isEmailVerified = false
        )

        logger.info("ユーザーを作成しました: id=${user.id}, email=${user.email}")

        // メール認証トークンを生成
        val verificationToken = TokenGenerator.generateEmailVerificationToken()
        val expiresAt = Instant.now().plus(24, ChronoUnit.HOURS) // 24時間有効

        // トークンをデータベースに保存
        emailVerificationRepository.create(
            userId = user.id,
            token = verificationToken,
            expiresAt = expiresAt
        )

        logger.info("メール認証トークンを生成しました: userId=${user.id}")

        // 認証メールを送信
        emailService.sendVerificationEmail(
            to = user.email,
            token = verificationToken,
            userName = user.displayName
        )

        logger.info("認証メールを送信しました: ${user.email}")

        return RegisterResponse(
            user = user.toDto(),
            message = "確認メールを送信しました。メールを確認してアカウントを有効化してください。"
        )
    }

    /**
     * メール認証
     *
     * @param token 認証トークン
     * @return 認証レスポンス
     * @throws NotFoundException トークンが無効または期限切れの場合
     */
    fun verifyEmail(token: String): VerifyEmailResponse {
        logger.info("メール認証を開始")

        // トークンを検索
        val (tokenId, userId) = emailVerificationRepository.findValidToken(token)
            ?: run {
                logger.warn("無効な認証トークンが使用されました")
                throw NotFoundException("認証トークンが無効または期限切れです")
            }

        logger.info("有効な認証トークンを確認しました: userId=$userId")

        // ユーザーのメール認証状態を更新
        val updated = userRepository.updateEmailVerified(userId, isVerified = true)
        if (!updated) {
            logger.error("ユーザーが見つかりません: userId=$userId")
            throw NotFoundException("ユーザーが見つかりません")
        }

        // トークンを使用済みにする
        emailVerificationRepository.markAsUsed(tokenId)

        logger.info("メール認証が完了しました: userId=$userId")

        // ウェルカムメールを送信
        val user = userRepository.findById(userId)
        user?.let {
            emailService.sendWelcomeEmail(
                to = it.email,
                userName = it.displayName
            )
        }

        return VerifyEmailResponse(
            message = "メールアドレスの認証が完了しました"
        )
    }

    /**
     * メール認証トークンを再送
     *
     * @param email メールアドレス
     * @throws NotFoundException ユーザーが見つからない場合
     * @throws IllegalArgumentException 既に認証済みの場合
     */
    fun resendVerificationEmail(email: String) {
        logger.info("メール認証トークンの再送を開始: $email")

        val user = userRepository.findByEmail(email)
            ?: throw NotFoundException("ユーザーが見つかりません")

        if (user.isEmailVerified) {
            throw IllegalArgumentException("このアカウントは既に認証済みです")
        }

        // 既存の未使用トークンを削除
        emailVerificationRepository.deleteUnusedTokensByUserId(user.id)

        // 新しいトークンを生成
        val verificationToken = TokenGenerator.generateEmailVerificationToken()
        val expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)

        emailVerificationRepository.create(
            userId = user.id,
            token = verificationToken,
            expiresAt = expiresAt
        )

        // 認証メールを送信
        emailService.sendVerificationEmail(
            to = user.email,
            token = verificationToken,
            userName = user.displayName
        )

        logger.info("メール認証トークンを再送しました: ${user.email}")
    }

    /**
     * ログイン
     *
     * @param request ログインリクエスト
     * @return ログインレスポンス
     * @throws UnauthorizedException 認証情報が無効な場合
     * @throws ForbiddenException メール未認証の場合
     */
    fun login(request: LoginRequest): LoginResponse {
        logger.info("ログインを開始: ${request.email}")

        // ユーザーを検索
        val user = userRepository.findByEmail(request.email)
            ?: run {
                logger.warn("ログイン失敗: ユーザーが見つかりません - ${request.email}")
                throw UnauthorizedException("メールアドレスまたはパスワードが正しくありません")
            }

        // パスワードが設定されていない（OAuth専用ユーザー）
        if (user.passwordHash == null) {
            logger.warn("ログイン失敗: パスワード未設定 - ${request.email}")
            throw UnauthorizedException("このアカウントはソーシャルログイン専用です")
        }

        // パスワード検証
        if (!PasswordHasher.verifyPassword(request.password, user.passwordHash)) {
            logger.warn("ログイン失敗: パスワードが正しくありません - ${request.email}")
            throw UnauthorizedException("メールアドレスまたはパスワードが正しくありません")
        }

        // メール認証チェック
        if (!user.isEmailVerified) {
            logger.warn("ログイン失敗: メール未認証 - ${request.email}")
            throw ForbiddenException("メールアドレスの認証が完了していません")
        }

        logger.info("ログイン成功: userId=${user.id}, email=${user.email}")

        // トークンを生成
        return generateTokens(user)
    }

    /**
     * トークン更新
     *
     * @param refreshToken リフレッシュトークン
     * @return トークン更新レスポンス
     * @throws UnauthorizedException リフレッシュトークンが無効な場合
     */
    fun refreshToken(refreshToken: String): RefreshTokenResponse {
        logger.info("トークン更新を開始")

        // リフレッシュトークンをハッシュ化
        val tokenHash = TokenGenerator.hashToken(refreshToken)

        // トークンを検索
        val (tokenId, userId) = refreshTokenRepository.findValidToken(tokenHash)
            ?: run {
                logger.warn("無効なリフレッシュトークンが使用されました")
                throw UnauthorizedException("リフレッシュトークンが無効または期限切れです")
            }

        logger.info("有効なリフレッシュトークンを確認しました: userId=$userId")

        // ユーザーを検索
        val user = userRepository.findById(userId)
            ?: run {
                logger.error("ユーザーが見つかりません: userId=$userId")
                throw UnauthorizedException("ユーザーが見つかりません")
            }

        // 古いトークンを無効化
        refreshTokenRepository.revoke(tokenId)

        logger.info("トークン更新成功: userId=$userId")

        // 新しいトークンを生成
        val accessToken = tokenService.generateAccessToken(user)
        val newRefreshToken = TokenGenerator.generateRefreshToken()
        val newRefreshTokenHash = TokenGenerator.hashToken(newRefreshToken)
        val expiresAt = Instant.now().plus(JwtConfig.refreshTokenExpiry, ChronoUnit.MILLIS)

        // 新しいリフレッシュトークンを保存
        refreshTokenRepository.create(
            userId = user.id,
            tokenHash = newRefreshTokenHash,
            expiresAt = expiresAt
        )

        return RefreshTokenResponse(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            expiresIn = JwtConfig.getAccessTokenExpiryInSeconds()
        )
    }

    /**
     * ログアウト
     *
     * @param refreshToken リフレッシュトークン
     * @return ログアウトレスポンス
     */
    fun logout(refreshToken: String): LogoutResponse {
        logger.info("ログアウトを開始")

        // リフレッシュトークンをハッシュ化
        val tokenHash = TokenGenerator.hashToken(refreshToken)

        // トークンを無効化
        val revoked = refreshTokenRepository.revokeByHash(tokenHash)

        if (revoked) {
            logger.info("ログアウト成功: トークンを無効化しました")
        } else {
            logger.warn("ログアウト: トークンが見つかりませんでした")
        }

        return LogoutResponse(
            message = "ログアウトしました"
        )
    }

    /**
     * パスワードリセット要求
     *
     * @param email メールアドレス
     * @return パスワードリセットレスポンス
     */
    fun requestPasswordReset(email: String): PasswordResetResponse {
        logger.info("パスワードリセット要求: $email")

        // セキュリティのため、ユーザーが存在しない場合も同じレスポンスを返す
        val user = userRepository.findByEmail(email)

        if (user != null) {
            // パスワードが設定されていない（OAuth専用ユーザー）
            if (user.passwordHash == null) {
                logger.warn("パスワードリセット要求: OAuth専用ユーザー - $email")
                // セキュリティのため、同じレスポンスを返す
            } else {
                // 既存の未使用トークンを削除
                passwordResetRepository.deleteUnusedTokensByUserId(user.id)

                // 新しいトークンを生成
                val resetToken = TokenGenerator.generatePasswordResetToken()
                val expiresAt = Instant.now().plus(1, ChronoUnit.HOURS) // 1時間有効

                passwordResetRepository.create(
                    userId = user.id,
                    token = resetToken,
                    expiresAt = expiresAt
                )

                logger.info("パスワードリセットトークンを生成しました: userId=${user.id}")

                // リセットメールを送信
                emailService.sendPasswordResetEmail(
                    to = user.email,
                    token = resetToken,
                    userName = user.displayName
                )

                logger.info("パスワードリセットメールを送信しました: $email")
            }
        } else {
            logger.warn("パスワードリセット要求: ユーザーが見つかりません - $email")
            // セキュリティのため、ユーザーが存在しない場合も同じレスポンスを返す
        }

        return PasswordResetResponse(
            message = "パスワードリセット用のメールを送信しました"
        )
    }

    /**
     * パスワードリセット実行
     *
     * @param request パスワードリセット確認リクエスト
     * @return パスワードリセット確認レスポンス
     * @throws NotFoundException トークンが無効または期限切れの場合
     */
    fun confirmPasswordReset(request: PasswordResetConfirmRequest): PasswordResetConfirmResponse {
        logger.info("パスワードリセット実行を開始")

        // トークンを検索
        val (tokenId, userId) = passwordResetRepository.findValidToken(request.token)
            ?: run {
                logger.warn("無効なリセットトークンが使用されました")
                throw NotFoundException("リセットトークンが無効または期限切れです")
            }

        logger.info("有効なリセットトークンを確認しました: userId=$userId")

        // 新しいパスワードをハッシュ化
        val newPasswordHash = PasswordHasher.hashPassword(request.newPassword)

        // パスワードを更新
        userRepository.updatePassword(userId, newPasswordHash)

        // トークンを使用済みにする
        passwordResetRepository.markAsUsed(tokenId)

        // セキュリティのため、全てのリフレッシュトークンを無効化
        refreshTokenRepository.revokeAllByUserId(userId)

        logger.info("パスワードリセットが完了しました: userId=$userId")

        return PasswordResetConfirmResponse(
            message = "パスワードがリセットされました"
        )
    }

    /**
     * トークンを生成してレスポンスを返す
     */
    private fun generateTokens(user: User): LoginResponse {
        // Access Tokenを生成
        val accessToken = tokenService.generateAccessToken(user)

        // Refresh Tokenを生成
        val refreshToken = TokenGenerator.generateRefreshToken()
        val refreshTokenHash = TokenGenerator.hashToken(refreshToken)
        val expiresAt = Instant.now().plus(JwtConfig.refreshTokenExpiry, ChronoUnit.MILLIS)

        // リフレッシュトークンをデータベースに保存
        refreshTokenRepository.create(
            userId = user.id,
            tokenHash = refreshTokenHash,
            expiresAt = expiresAt
        )

        logger.info("トークンを生成しました: userId=${user.id}")

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = JwtConfig.getAccessTokenExpiryInSeconds(),
            user = user.toDto()
        )
    }
}
