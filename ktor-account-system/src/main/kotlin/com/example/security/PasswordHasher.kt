package com.example.security

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * パスワードハッシュ化ユーティリティ
 */
object PasswordHasher {
    private const val COST = 12 // BCryptのコストファクター（推奨値: 12）

    /**
     * パスワードをハッシュ化する
     *
     * @param password 平文パスワード
     * @return ハッシュ化されたパスワード
     */
    fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray())
    }

    /**
     * パスワードを検証する
     *
     * @param password 平文パスワード
     * @param hash ハッシュ化されたパスワード
     * @return パスワードが一致する場合はtrue
     */
    fun verifyPassword(password: String, hash: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), hash.toCharArray())
        return result.verified
    }
}
