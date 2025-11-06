package com.example.security

/**
 * JWT設定
 */
object JwtConfig {
    // 環境変数から取得
    val secret: String = System.getenv("JWT_SECRET")
        ?: "your-secret-key-change-in-production-min-256-bits"

    val issuer: String = System.getenv("JWT_ISSUER")
        ?: "account-system"

    val audience: String = System.getenv("JWT_AUDIENCE")
        ?: "account-system-users"

    val realm: String = "ktor-account-system"

    // トークンの有効期限（ミリ秒）
    val accessTokenExpiry: Long = System.getenv("JWT_ACCESS_TOKEN_EXPIRY")?.toLongOrNull()
        ?: 900_000L  // 15分

    val refreshTokenExpiry: Long = System.getenv("JWT_REFRESH_TOKEN_EXPIRY")?.toLongOrNull()
        ?: 2_592_000_000L  // 30日

    /**
     * Access Tokenの有効期限（秒）
     */
    fun getAccessTokenExpiryInSeconds(): Long = accessTokenExpiry / 1000
}
