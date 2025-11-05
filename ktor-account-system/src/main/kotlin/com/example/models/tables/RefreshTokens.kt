package com.example.models.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object RefreshTokens : LongIdTable("refresh_tokens") {
    val userId = reference("user_id", Users)
    val tokenHash = varchar("token_hash", 255).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").default(Instant.now())
    val revokedAt = timestamp("revoked_at").nullable()
}
