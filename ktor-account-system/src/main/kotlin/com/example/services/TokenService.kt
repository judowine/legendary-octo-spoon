package com.example.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.models.entities.User
import com.example.security.JwtConfig
import org.slf4j.LoggerFactory
import java.util.*

/**
 * トークンサービス
 *
 * JWTの生成と検証を行う
 */
class TokenService {
    private val logger = LoggerFactory.getLogger(TokenService::class.java)
    private val algorithm = Algorithm.HMAC256(JwtConfig.secret)

    /**
     * Access Tokenを生成
     *
     * @param user ユーザー情報
     * @return JWT Access Token
     */
    fun generateAccessToken(user: User): String {
        val now = Date()
        val expiresAt = Date(now.time + JwtConfig.accessTokenExpiry)

        return JWT.create()
            .withAudience(JwtConfig.audience)
            .withIssuer(JwtConfig.issuer)
            .withSubject(user.id.toString())
            .withClaim("email", user.email)
            .withClaim("displayName", user.displayName)
            .withClaim("isEmailVerified", user.isEmailVerified)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    /**
     * JWTトークンを検証
     *
     * @param token JWTトークン
     * @return ユーザーID（検証失敗時はnull）
     */
    fun verifyToken(token: String): Long? {
        return try {
            val verifier = JWT.require(algorithm)
                .withAudience(JwtConfig.audience)
                .withIssuer(JwtConfig.issuer)
                .build()

            val decodedJWT = verifier.verify(token)
            decodedJWT.subject.toLongOrNull()
        } catch (e: Exception) {
            logger.warn("JWT検証に失敗しました: ${e.message}")
            null
        }
    }

    /**
     * JWTトークンからユーザーIDを取得（検証なし）
     *
     * @param token JWTトークン
     * @return ユーザーID（解析失敗時はnull）
     */
    fun getUserIdFromToken(token: String): Long? {
        return try {
            val decodedJWT = JWT.decode(token)
            decodedJWT.subject.toLongOrNull()
        } catch (e: Exception) {
            logger.warn("JWTデコードに失敗しました: ${e.message}")
            null
        }
    }

    /**
     * JWTトークンからメールアドレスを取得
     *
     * @param token JWTトークン
     * @return メールアドレス（解析失敗時はnull）
     */
    fun getEmailFromToken(token: String): String? {
        return try {
            val decodedJWT = JWT.decode(token)
            decodedJWT.getClaim("email").asString()
        } catch (e: Exception) {
            logger.warn("JWTデコードに失敗しました: ${e.message}")
            null
        }
    }
}
