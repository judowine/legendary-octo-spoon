package com.example.services

import com.example.models.dto.RegisterRequest
import com.example.models.dto.RegisterResponse
import com.example.models.dto.VerifyEmailResponse
import com.example.models.entities.User
import com.example.plugins.ConflictException
import com.example.plugins.NotFoundException
import com.example.repositories.EmailVerificationRepository
import com.example.repositories.UserRepository
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
    private val emailService: EmailService = EmailService()
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
}
