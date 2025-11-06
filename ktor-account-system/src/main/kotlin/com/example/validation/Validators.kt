package com.example.validation

import com.example.models.dto.*
import io.konform.validation.Validation
import io.konform.validation.ValidationResult
import io.konform.validation.jsonschema.minLength
import io.konform.validation.jsonschema.pattern

/**
 * バリデーションの結果を例外として扱う
 */
class ValidationException(
    val errors: Map<String, List<String>>
) : Exception("バリデーションエラーが発生しました")

/**
 * バリデーション結果を検証し、エラーがあれば例外をスローする
 */
fun <T> ValidationResult<T>.getOrThrow(): T {
    return when (this) {
        is ValidationResult.Valid -> this.value
        is ValidationResult.Invalid -> {
            val errorMap = this.errors.groupBy(
                keySelector = { it.dataPath },
                valueTransform = { it.message }
            )
            throw ValidationException(errorMap)
        }
    }
}

/**
 * メールアドレスのパターン
 */
private val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()

/**
 * パスワードのパターン（最小8文字、英大文字・小文字・数字を各1文字以上）
 */
private val passwordPattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$".toRegex()

/**
 * ユーザー登録リクエストのバリデーター
 */
val registerRequestValidator = Validation<RegisterRequest> {
    RegisterRequest::email {
        minLength(1) hint "メールアドレスは必須です"
        pattern(emailPattern) hint "有効なメールアドレスを入力してください"
    }

    RegisterRequest::password {
        minLength(8) hint "パスワードは8文字以上である必要があります"
        pattern(passwordPattern) hint "パスワードは英大文字・小文字・数字を各1文字以上含む必要があります"
    }

    RegisterRequest::displayName ifPresent {
        minLength(1) hint "表示名は1文字以上である必要があります"
    }
}

/**
 * メール認証リクエストのバリデーター
 */
val verifyEmailRequestValidator = Validation<VerifyEmailRequest> {
    VerifyEmailRequest::token {
        minLength(1) hint "トークンは必須です"
    }
}

/**
 * ログインリクエストのバリデーター
 */
val loginRequestValidator = Validation<LoginRequest> {
    LoginRequest::email {
        minLength(1) hint "メールアドレスは必須です"
        pattern(emailPattern) hint "有効なメールアドレスを入力してください"
    }

    LoginRequest::password {
        minLength(1) hint "パスワードは必須です"
    }
}

/**
 * プロフィール更新リクエストのバリデーター
 */
val updateProfileRequestValidator = Validation<UpdateProfileRequest> {
    UpdateProfileRequest::displayName ifPresent {
        minLength(1) hint "表示名は1文字以上である必要があります"
    }
}

/**
 * メールアドレス変更リクエストのバリデーター
 */
val changeEmailRequestValidator = Validation<ChangeEmailRequest> {
    ChangeEmailRequest::newEmail {
        minLength(1) hint "新しいメールアドレスは必須です"
        pattern(emailPattern) hint "有効なメールアドレスを入力してください"
    }

    ChangeEmailRequest::password {
        minLength(1) hint "パスワードは必須です"
    }
}

/**
 * パスワード変更リクエストのバリデーター
 */
val changePasswordRequestValidator = Validation<ChangePasswordRequest> {
    ChangePasswordRequest::currentPassword {
        minLength(1) hint "現在のパスワードは必須です"
    }

    ChangePasswordRequest::newPassword {
        minLength(8) hint "新しいパスワードは8文字以上である必要があります"
        pattern(passwordPattern) hint "新しいパスワードは英大文字・小文字・数字を各1文字以上含む必要があります"
    }
}

/**
 * パスワードリセット要求リクエストのバリデーター
 */
val passwordResetRequestValidator = Validation<PasswordResetRequest> {
    PasswordResetRequest::email {
        minLength(1) hint "メールアドレスは必須です"
        pattern(emailPattern) hint "有効なメールアドレスを入力してください"
    }
}

/**
 * パスワードリセット確認リクエストのバリデーター
 */
val passwordResetConfirmRequestValidator = Validation<PasswordResetConfirmRequest> {
    PasswordResetConfirmRequest::token {
        minLength(1) hint "トークンは必須です"
    }

    PasswordResetConfirmRequest::newPassword {
        minLength(8) hint "新しいパスワードは8文字以上である必要があります"
        pattern(passwordPattern) hint "新しいパスワードは英大文字・小文字・数字を各1文字以上含む必要があります"
    }
}

/**
 * アカウント削除リクエストのバリデーター
 */
val deleteAccountRequestValidator = Validation<DeleteAccountRequest> {
    DeleteAccountRequest::confirmation {
        pattern("DELETE_MY_ACCOUNT".toRegex()) hint "確認文字列は 'DELETE_MY_ACCOUNT' である必要があります"
    }
}
