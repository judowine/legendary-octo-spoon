package com.example.security

import java.security.SecureRandom
import java.util.*

/**
 * トークン生成ユーティリティ
 */
object TokenGenerator {
    private val secureRandom = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /**
     * ランダムなトークンを生成する
     *
     * @param length バイト長（デフォルト: 32バイト = 256ビット）
     * @return Base64エンコードされたトークン
     */
    fun generateToken(length: Int = 32): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    /**
     * メール認証用のトークンを生成する
     *
     * @return メール認証トークン
     */
    fun generateEmailVerificationToken(): String {
        return generateToken(32) // 256ビット
    }

    /**
     * パスワードリセット用のトークンを生成する
     *
     * @return パスワードリセットトークン
     */
    fun generatePasswordResetToken(): String {
        return generateToken(32) // 256ビット
    }

    /**
     * リフレッシュトークンを生成する
     *
     * @return リフレッシュトークン
     */
    fun generateRefreshToken(): String {
        return generateToken(64) // 512ビット
    }

    /**
     * トークンをハッシュ化する（データベース保存用）
     *
     * @param token トークン
     * @return SHA-256ハッシュ
     */
    fun hashToken(token: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray())
        return encoder.encodeToString(hashBytes)
    }
}
