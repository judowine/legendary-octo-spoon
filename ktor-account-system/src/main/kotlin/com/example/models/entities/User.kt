package com.example.models.entities

import com.example.models.dto.UserDto
import java.time.Instant

/**
 * ユーザーエンティティ
 */
data class User(
    val id: Long,
    val email: String,
    val passwordHash: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?
) {
    /**
     * DTOに変換
     */
    fun toDto(): UserDto = UserDto(
        id = id,
        email = email,
        displayName = displayName,
        isEmailVerified = isEmailVerified,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}
