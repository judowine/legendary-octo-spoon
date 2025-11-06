package com.example.routes

import com.example.models.dto.*
import com.example.plugins.*
import com.example.services.UserService
import com.example.utils.getUserId
import com.example.validation.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

fun Route.userRoutes() {
    val userService = UserService()

    route("/users") {
        // JWT認証が必要なエンドポイント
        authenticate("auth-jwt") {
            // プロフィール取得
            get("/me") {
                try {
                    val userId = call.getUserId()
                    val userDto = userService.getProfile(userId)
                    call.respond(HttpStatusCode.OK, userDto)
                } catch (e: NotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(
                            error = "NotFound",
                            message = e.message ?: "ユーザーが見つかりません",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                } catch (e: UnauthorizedException) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(
                            error = "Unauthorized",
                            message = e.message ?: "認証が必要です",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                }
            }

            // プロフィール更新
            patch("/me") {
                try {
                    val userId = call.getUserId()
                    val request = call.receive<UpdateProfileRequest>()

                    // バリデーション
                    updateProfileRequestValidator(request).getOrThrow()

                    val userDto = userService.updateProfile(userId, request)
                    call.respond(HttpStatusCode.OK, userDto)
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
                        HttpStatusCode.NotFound,
                        ErrorResponse(
                            error = "NotFound",
                            message = e.message ?: "ユーザーが見つかりません",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                } catch (e: UnauthorizedException) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(
                            error = "Unauthorized",
                            message = e.message ?: "認証が必要です",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                }
            }

            // メールアドレス変更
            post("/me/email") {
                try {
                    val userId = call.getUserId()
                    val request = call.receive<ChangeEmailRequest>()

                    // バリデーション
                    changeEmailRequestValidator(request).getOrThrow()

                    val response = userService.changeEmail(userId, request)
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
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            error = "InvalidPassword",
                            message = e.message ?: "パスワードが正しくありません",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                } catch (e: ConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(
                            error = "EmailAlreadyExists",
                            message = e.message ?: "このメールアドレスは既に使用されています",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                } catch (e: NotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(
                            error = "NotFound",
                            message = e.message ?: "ユーザーが見つかりません",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                }
            }

            // パスワード変更
            post("/me/password") {
                try {
                    val userId = call.getUserId()
                    val request = call.receive<ChangePasswordRequest>()

                    // バリデーション
                    changePasswordRequestValidator(request).getOrThrow()

                    val response = userService.changePassword(userId, request)
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
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            error = "InvalidPassword",
                            message = e.message ?: "現在のパスワードが正しくありません",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                } catch (e: NotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(
                            error = "NotFound",
                            message = e.message ?: "ユーザーが見つかりません",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                }
            }

            // アカウント削除
            delete("/me") {
                try {
                    val userId = call.getUserId()
                    val request = call.receive<DeleteAccountRequest>()

                    // バリデーション
                    deleteAccountRequestValidator(request).getOrThrow()

                    val response = userService.deleteAccount(userId, request)
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
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            error = "InvalidPassword",
                            message = e.message ?: "パスワードが正しくありません",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                } catch (e: NotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(
                            error = "NotFound",
                            message = e.message ?: "ユーザーが見つかりません",
                            timestamp = Clock.System.now().toString(),
                            path = call.request.path()
                        )
                    )
                }
            }
        }
    }
}
