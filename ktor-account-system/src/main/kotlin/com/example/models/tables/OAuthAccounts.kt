package com.example.models.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object OAuthAccounts : LongIdTable("oauth_accounts") {
    val userId = reference("user_id", Users)
    val provider = varchar("provider", 50) // 'google', 'github'
    val providerUserId = varchar("provider_user_id", 255)
    val accessToken = text("access_token").nullable()
    val refreshToken = text("refresh_token").nullable()
    val expiresAt = timestamp("expires_at").nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())

    init {
        uniqueIndex(provider, providerUserId)
    }
}
