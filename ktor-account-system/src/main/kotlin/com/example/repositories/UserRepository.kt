package com.example.repositories

import com.example.models.entities.User
import com.example.models.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * ユーザーリポジトリ
 */
class UserRepository {

    /**
     * ユーザーをIDで検索
     */
    fun findById(id: Long): User? = transaction {
        Users.select { (Users.id eq id) and Users.deletedAt.isNull() }
            .singleOrNull()
            ?.toUser()
    }

    /**
     * ユーザーをメールアドレスで検索
     */
    fun findByEmail(email: String): User? = transaction {
        Users.select { (Users.email eq email) and Users.deletedAt.isNull() }
            .singleOrNull()
            ?.toUser()
    }

    /**
     * メールアドレスが既に存在するか確認
     */
    fun existsByEmail(email: String): Boolean = transaction {
        Users.select { (Users.email eq email) and Users.deletedAt.isNull() }
            .count() > 0
    }

    /**
     * ユーザーを作成
     */
    fun create(
        email: String,
        passwordHash: String?,
        displayName: String?,
        isEmailVerified: Boolean = false
    ): User = transaction {
        val now = Instant.now()
        val id = Users.insertAndGetId {
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.displayName] = displayName
            it[Users.isEmailVerified] = isEmailVerified
            it[createdAt] = now
            it[updatedAt] = now
        }

        User(
            id = id.value,
            email = email,
            passwordHash = passwordHash,
            displayName = displayName,
            isEmailVerified = isEmailVerified,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
    }

    /**
     * ユーザーを更新
     */
    fun update(
        id: Long,
        email: String? = null,
        passwordHash: String? = null,
        displayName: String? = null,
        isEmailVerified: Boolean? = null
    ): User? = transaction {
        val updateCount = Users.update({ Users.id eq id }) {
            email?.let { value -> it[Users.email] = value }
            passwordHash?.let { value -> it[Users.passwordHash] = value }
            displayName?.let { value -> it[Users.displayName] = value }
            isEmailVerified?.let { value -> it[Users.isEmailVerified] = value }
            it[updatedAt] = Instant.now()
        }

        if (updateCount > 0) findById(id) else null
    }

    /**
     * メール認証状態を更新
     */
    fun updateEmailVerified(id: Long, isVerified: Boolean): Boolean = transaction {
        Users.update({ Users.id eq id }) {
            it[isEmailVerified] = isVerified
            it[updatedAt] = Instant.now()
        } > 0
    }

    /**
     * パスワードを更新
     */
    fun updatePassword(id: Long, newPasswordHash: String): Boolean = transaction {
        Users.update({ Users.id eq id }) {
            it[passwordHash] = newPasswordHash
            it[updatedAt] = Instant.now()
        } > 0
    }

    /**
     * ユーザーを削除（論理削除）
     */
    fun delete(id: Long): Boolean = transaction {
        Users.update({ Users.id eq id }) {
            it[deletedAt] = Instant.now()
        } > 0
    }

    /**
     * ResultRowをUserエンティティに変換
     */
    private fun ResultRow.toUser() = User(
        id = this[Users.id].value,
        email = this[Users.email],
        passwordHash = this[Users.passwordHash],
        displayName = this[Users.displayName],
        isEmailVerified = this[Users.isEmailVerified],
        createdAt = this[Users.createdAt],
        updatedAt = this[Users.updatedAt],
        deletedAt = this[Users.deletedAt]
    )
}
