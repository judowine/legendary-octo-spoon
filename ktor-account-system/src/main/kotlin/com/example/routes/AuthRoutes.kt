package com.example.routes

import com.example.models.dto.ErrorResponse
import com.example.models.dto.RegisterRequest
import com.example.models.dto.VerifyEmailRequest
import com.example.plugins.ConflictException
import com.example.plugins.NotFoundException
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

        // TODO: その他の認証エンドポイント
        // - POST /login - ログイン
        // - POST /refresh - トークン更新
        // - POST /logout - ログアウト
        // - POST /password-reset/request - パスワードリセット要求
        // - POST /password-reset/confirm - パスワードリセット実行
        // - GET /oauth/{provider} - OAuth認証開始
        // - GET /oauth/{provider}/callback - OAuthコールバック
    }
}
