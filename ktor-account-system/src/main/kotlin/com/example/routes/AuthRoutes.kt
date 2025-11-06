package com.example.routes

import com.example.models.dto.*
import com.example.plugins.*
import com.example.services.AuthService
import com.example.validation.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

fun Route.authRoutes() {
    val authService = AuthService()

    route("/auth") {
        // ユーザー登録
        post("/register") {
            try {
                // リクエストボディを受け取る
                val request = call.receive<RegisterRequest>()

                // バリデーション
                registerRequestValidator(request).getOrThrow()

                // ユーザー登録処理
                val response = authService.register(request)

                call.respond(HttpStatusCode.Created, response)
            } catch (e: ValidationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        error = "ValidationError",
                        message = "入力内容に誤りがあります",
                        fields = e.errors.mapValues { it.value.joinToString(", ") },
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            } catch (e: ConflictException) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(
                        error = "EmailAlreadyExists",
                        message = e.message ?: "このメールアドレスは既に登録されています",
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            }
        }

        // メール認証
        post("/verify-email") {
            try {
                // リクエストボディを受け取る
                val request = call.receive<VerifyEmailRequest>()

                // バリデーション
                verifyEmailRequestValidator(request).getOrThrow()

                // メール認証処理
                val response = authService.verifyEmail(request.token)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: ValidationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        error = "ValidationError",
                        message = "入力内容に誤りがあります",
                        fields = e.errors.mapValues { it.value.joinToString(", ") },
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            } catch (e: NotFoundException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        error = "InvalidToken",
                        message = e.message ?: "認証トークンが無効または期限切れです",
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            }
        }

        // ログイン
        post("/login") {
            try {
                val request = call.receive<LoginRequest>()

                // バリデーション
                loginRequestValidator(request).getOrThrow()

                // ログイン処理
                val response = authService.login(request)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: ValidationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        error = "ValidationError",
                        message = "入力内容に誤りがあります",
                        fields = e.errors.mapValues { it.value.joinToString(", ") },
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            } catch (e: UnauthorizedException) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        error = "InvalidCredentials",
                        message = e.message ?: "メールアドレスまたはパスワードが正しくありません",
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            } catch (e: ForbiddenException) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse(
                        error = "EmailNotVerified",
                        message = e.message ?: "メールアドレスの認証が完了していません",
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            }
        }

        // トークン更新
        post("/refresh") {
            try {
                val request = call.receive<RefreshTokenRequest>()

                // トークン更新処理
                val response = authService.refreshToken(request.refreshToken)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: UnauthorizedException) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        error = "InvalidRefreshToken",
                        message = e.message ?: "リフレッシュトークンが無効または期限切れです",
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            }
        }

        // ログアウト
        post("/logout") {
            try {
                val request = call.receive<LogoutRequest>()

                // ログアウト処理
                val response = authService.logout(request.refreshToken)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(
                        error = "InternalServerError",
                        message = "ログアウト処理中にエラーが発生しました",
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            }
        }

        // パスワードリセット要求
        post("/password-reset/request") {
            try {
                val request = call.receive<PasswordResetRequest>()

                // バリデーション
                passwordResetRequestValidator(request).getOrThrow()

                // パスワードリセット要求処理
                val response = authService.requestPasswordReset(request.email)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: ValidationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        error = "ValidationError",
                        message = "入力内容に誤りがあります",
                        fields = e.errors.mapValues { it.value.joinToString(", ") },
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            }
        }

        // パスワードリセット実行
        post("/password-reset/confirm") {
            try {
                val request = call.receive<PasswordResetConfirmRequest>()

                // バリデーション
                passwordResetConfirmRequestValidator(request).getOrThrow()

                // パスワードリセット実行処理
                val response = authService.confirmPasswordReset(request)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: ValidationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        error = "ValidationError",
                        message = "入力内容に誤りがあります",
                        fields = e.errors.mapValues { it.value.joinToString(", ") },
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            } catch (e: NotFoundException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        error = "InvalidToken",
                        message = e.message ?: "リセットトークンが無効または期限切れです",
                        timestamp = Clock.System.now().toString(),
                        path = call.request.path()
                    )
                )
            }
        }

        // TODO: その他の認証エンドポイント
        // - GET /oauth/{provider} - OAuth認証開始
        // - GET /oauth/{provider}/callback - OAuthコールバック
    }
}
