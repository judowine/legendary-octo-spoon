package com.example.repositories

import com.example.models.tables.RefreshTokens
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * リフレッシュトークンリポジトリ
 */
class RefreshTokenRepository {

    /**
     * リフレッシュトークンを作成
     */
    fun create(userId: Long, tokenHash: String, expiresAt: Instant): Long = transaction {
        RefreshTokens.insertAndGetId {
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.tokenHash] = tokenHash
            it[RefreshTokens.expiresAt] = expiresAt
            it[createdAt] = Instant.now()
        }.value
    }

    /**
     * トークンハッシュで検索（有効なもののみ）
     */
    fun findValidToken(tokenHash: String): Pair<Long, Long>? = transaction {
        RefreshTokens
            .select {
                (RefreshTokens.tokenHash eq tokenHash) and
                        RefreshTokens.revokedAt.isNull() and
                        (RefreshTokens.expiresAt greater Instant.now())
            }
            .singleOrNull()
            ?.let {
                Pair(it[RefreshTokens.id].value, it[RefreshTokens.userId].value)
            }
    }

    /**
     * トークンを無効化（revoke）
     */
    fun revoke(tokenId: Long): Boolean = transaction {
        RefreshTokens.update({ RefreshTokens.id eq tokenId }) {
            it[revokedAt] = Instant.now()
        } > 0
    }

    /**
     * トークンハッシュで無効化
     */
    fun revokeByHash(tokenHash: String): Boolean = transaction {
        RefreshTokens.update({ RefreshTokens.tokenHash eq tokenHash }) {
            it[revokedAt] = Instant.now()
        } > 0
    }

    /**
     * ユーザーの全トークンを無効化
     */
    fun revokeAllByUserId(userId: Long): Int = transaction {
        RefreshTokens.update({
            (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull()
        }) {
            it[revokedAt] = Instant.now()
        }
    }

    /**
     * 期限切れトークンを削除（クリーンアップ用）
     */
    fun deleteExpiredTokens(): Int = transaction {
        RefreshTokens.deleteWhere {
            expiresAt less Instant.now()
        }
    }

    /**
     * 無効化されたトークンを削除（クリーンアップ用）
     */
    fun deleteRevokedTokens(): Int = transaction {
        RefreshTokens.deleteWhere {
            revokedAt.isNotNull()
        }
    }
}
