package com.example.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity() {
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: "your-secret-key-change-in-production-min-256-bits"
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "account-system"
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "account-system-users"
    val jwtRealm = "ktor-account-system"

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
