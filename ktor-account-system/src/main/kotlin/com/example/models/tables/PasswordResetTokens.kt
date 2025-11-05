package com.example.models.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object PasswordResetTokens : LongIdTable("password_reset_tokens") {
    val userId = reference("user_id", Users)
    val token = varchar("token", 255).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").default(Instant.now())
    val usedAt = timestamp("used_at").nullable()
}
