package com.example.plugins

import com.example.models.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.datetime.Clock

fun Application.configureStatusPages() {
    install(StatusPages) {
        // 400 Bad Request
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "BadRequest",
                    message = cause.message ?: "不正なリクエストです",
                    timestamp = Clock.System.now().toString(),
                    path = call.request.local.uri
                )
            )
        }

        // 401 Unauthorized
        exception<UnauthorizedException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(
                    error = "Unauthorized",
                    message = cause.message ?: "認証が必要です",
                    timestamp = Clock.System.now().toString(),
                    path = call.request.local.uri
                )
            )
        }

        // 403 Forbidden
        exception<ForbiddenException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(
                    error = "Forbidden",
                    message = cause.message ?: "アクセスが拒否されました",
                    timestamp = Clock.System.now().toString(),
                    path = call.request.local.uri
                )
            )
        }

        // 404 Not Found
        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = "NotFound",
                    message = cause.message ?: "リソースが見つかりません",
                    timestamp = Clock.System.now().toString(),
                    path = call.request.local.uri
                )
            )
        }

        // 409 Conflict
        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    error = "Conflict",
                    message = cause.message ?: "リソースが競合しています",
                    timestamp = Clock.System.now().toString(),
                    path = call.request.local.uri
                )
            )
        }

        // 500 Internal Server Error
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = "InternalServerError",
                    message = "サーバー内部エラーが発生しました",
                    timestamp = Clock.System.now().toString(),
                    path = call.request.local.uri
                )
            )
        }
    }
}

// カスタム例外クラス
class UnauthorizedException(message: String) : Exception(message)
class ForbiddenException(message: String) : Exception(message)
class NotFoundException(message: String) : Exception(message)
class ConflictException(message: String) : Exception(message)
