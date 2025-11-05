package com.example.plugins

import com.example.models.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases() {
    val database = Database.connect(createHikariDataSource())

    // テーブル作成（開発環境のみ）
    transaction(database) {
        SchemaUtils.create(
            Users,
            OAuthAccounts,
            RefreshTokens,
            EmailVerificationTokens,
            PasswordResetTokens
        )
    }
}

private fun Application.createHikariDataSource(): HikariDataSource {
    val config = HikariConfig().apply {
        jdbcUrl = environment.config.propertyOrNull("database.url")?.getString()
            ?: System.getenv("DATABASE_URL")
            ?: "jdbc:postgresql://localhost:5432/account_system"

        username = environment.config.propertyOrNull("database.user")?.getString()
            ?: System.getenv("DATABASE_USER")
            ?: "admin"

        password = environment.config.propertyOrNull("database.password")?.getString()
            ?: System.getenv("DATABASE_PASSWORD")
            ?: "admin_password"

        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"

        validate()
    }

    return HikariDataSource(config)
}
