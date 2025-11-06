package com.example.services

import org.slf4j.LoggerFactory

/**
 * メール送信サービス（モック実装）
 *
 * 本番環境では実際のSMTPサーバーやSendGrid等の外部サービスを使用します。
 * 開発環境ではMailHogやログ出力を使用します。
 */
class EmailService {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    /**
     * メール認証用のメールを送信
     */
    fun sendVerificationEmail(to: String, token: String, userName: String?) {
        val verificationLink = buildVerificationLink(token)

        logger.info("""

            ========================================
            📧 メール認証メール送信
            ========================================
            To: $to
            件名: メールアドレスの確認

            ${userName ?: "ユーザー"}様

            ご登録ありがとうございます。
            以下のリンクをクリックして、メールアドレスの確認を完了してください。

            確認リンク: $verificationLink

            このトークンは24時間有効です。

            ※このメールに心当たりがない場合は、このメールを無視してください。
            ========================================

        """.trimIndent())
    }

    /**
     * パスワードリセット用のメールを送信
     */
    fun sendPasswordResetEmail(to: String, token: String, userName: String?) {
        val resetLink = buildPasswordResetLink(token)

        logger.info("""

            ========================================
            📧 パスワードリセットメール送信
            ========================================
            To: $to
            件名: パスワードリセットのご案内

            ${userName ?: "ユーザー"}様

            パスワードリセットのリクエストを受け付けました。
            以下のリンクをクリックして、新しいパスワードを設定してください。

            リセットリンク: $resetLink

            このトークンは1時間有効です。

            ※このメールに心当たりがない場合は、アカウントのセキュリティを確認してください。
            ========================================

        """.trimIndent())
    }

    /**
     * メールアドレス変更確認用のメールを送信
     */
    fun sendEmailChangeConfirmation(to: String, token: String, userName: String?) {
        val confirmationLink = buildEmailChangeConfirmationLink(token)

        logger.info("""

            ========================================
            📧 メールアドレス変更確認メール送信
            ========================================
            To: $to
            件名: メールアドレス変更の確認

            ${userName ?: "ユーザー"}様

            メールアドレスの変更リクエストを受け付けました。
            以下のリンクをクリックして、新しいメールアドレスを確認してください。

            確認リンク: $confirmationLink

            このトークンは24時間有効です。

            ※このメールに心当たりがない場合は、直ちにアカウントのセキュリティを確認してください。
            ========================================

        """.trimIndent())
    }

    /**
     * ウェルカムメールを送信
     */
    fun sendWelcomeEmail(to: String, userName: String?) {
        logger.info("""

            ========================================
            📧 ウェルカムメール送信
            ========================================
            To: $to
            件名: ご登録ありがとうございます

            ${userName ?: "ユーザー"}様

            アカウント登録が完了しました！
            ご利用ありがとうございます。

            今後とも、よろしくお願いいたします。
            ========================================

        """.trimIndent())
    }

    /**
     * メール認証リンクを生成
     */
    private fun buildVerificationLink(token: String): String {
        // 本番環境では実際のフロントエンドURLを使用
        return "http://localhost:3000/verify-email?token=$token"
    }

    /**
     * パスワードリセットリンクを生成
     */
    private fun buildPasswordResetLink(token: String): String {
        // 本番環境では実際のフロントエンドURLを使用
        return "http://localhost:3000/reset-password?token=$token"
    }

    /**
     * メールアドレス変更確認リンクを生成
     */
    private fun buildEmailChangeConfirmationLink(token: String): String {
        // 本番環境では実際のフロントエンドURLを使用
        return "http://localhost:3000/confirm-email-change?token=$token"
    }
}
