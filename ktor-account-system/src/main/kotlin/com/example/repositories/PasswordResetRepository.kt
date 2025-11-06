package com.example.repositories

import com.example.models.tables.PasswordResetTokens
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * パスワードリセットトークンリポジトリ
 */
class PasswordResetRepository {

    /**
     * トークンを作成
     */
    fun create(userId: Long, token: String, expiresAt: Instant): Long = transaction {
        PasswordResetTokens.insertAndGetId {
            it[PasswordResetTokens.userId] = userId
            it[PasswordResetTokens.token] = token
            it[PasswordResetTokens.expiresAt] = expiresAt
            it[createdAt] = Instant.now()
        }.value
    }

    /**
     * トークンを検索（有効なもののみ）
     */
    fun findValidToken(token: String): Pair<Long, Long>? = transaction {
        PasswordResetTokens
            .select {
                (PasswordResetTokens.token eq token) and
                        PasswordResetTokens.usedAt.isNull() and
                        (PasswordResetTokens.expiresAt greater Instant.now())
            }
            .singleOrNull()
            ?.let {
                Pair(it[PasswordResetTokens.id].value, it[PasswordResetTokens.userId].value)
            }
    }

    /**
     * トークンを使用済みにする
     */
    fun markAsUsed(tokenId: Long): Boolean = transaction {
        PasswordResetTokens.update({ PasswordResetTokens.id eq tokenId }) {
            it[usedAt] = Instant.now()
        } > 0
    }

    /**
     * ユーザーの未使用トークンを削除
     */
    fun deleteUnusedTokensByUserId(userId: Long): Int = transaction {
        PasswordResetTokens.deleteWhere {
            (PasswordResetTokens.userId eq userId) and usedAt.isNull()
        }
    }

    /**
     * 期限切れトークンを削除（クリーンアップ用）
     */
    fun deleteExpiredTokens(): Int = transaction {
        PasswordResetTokens.deleteWhere {
            expiresAt less Instant.now()
        }
    }
}
