package com.example.repositories

import com.example.models.tables.EmailVerificationTokens
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * メール認証トークンリポジトリ
 */
class EmailVerificationRepository {

    /**
     * トークンを作成
     */
    fun create(userId: Long, token: String, expiresAt: Instant): Long = transaction {
        EmailVerificationTokens.insertAndGetId {
            it[EmailVerificationTokens.userId] = userId
            it[EmailVerificationTokens.token] = token
            it[EmailVerificationTokens.expiresAt] = expiresAt
            it[createdAt] = Instant.now()
        }.value
    }

    /**
     * トークンを検索（有効なもののみ）
     */
    fun findValidToken(token: String): Pair<Long, Long>? = transaction {
        EmailVerificationTokens
            .select {
                (EmailVerificationTokens.token eq token) and
                        EmailVerificationTokens.usedAt.isNull() and
                        (EmailVerificationTokens.expiresAt greater Instant.now())
            }
            .singleOrNull()
            ?.let {
                Pair(it[EmailVerificationTokens.id].value, it[EmailVerificationTokens.userId].value)
            }
    }

    /**
     * トークンを使用済みにする
     */
    fun markAsUsed(tokenId: Long): Boolean = transaction {
        EmailVerificationTokens.update({ EmailVerificationTokens.id eq tokenId }) {
            it[usedAt] = Instant.now()
        } > 0
    }

    /**
     * ユーザーの未使用トークンを削除
     */
    fun deleteUnusedTokensByUserId(userId: Long): Int = transaction {
        EmailVerificationTokens.deleteWhere {
            (EmailVerificationTokens.userId eq userId) and usedAt.isNull()
        }
    }

    /**
     * 期限切れトークンを削除（クリーンアップ用）
     */
    fun deleteExpiredTokens(): Int = transaction {
        EmailVerificationTokens.deleteWhere {
            expiresAt less Instant.now()
        }
    }
}
