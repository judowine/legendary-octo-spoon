# Part 4: モバイルBFFの認証パターン

## Chapter 4: OAuth 2.0とJWT（Ktorでの実装）

### 4.1 認証と認可の違い

**認証（Authentication）**: "あなたは誰ですか？"
- ログイン
- パスワード確認
- トークン検証

**認可（Authorization）**: "あなたは何ができますか？"
- アクセス権限
- ロール（管理者、一般ユーザー）
- リソースへのアクセス制御

---

### 4.2 モバイルアプリでの認証フロー

#### なぜBFFでトークンを管理するのか

**モバイルアプリの課題**:
- ❌ トークンをアプリ内に保存すると盗難リスク
- ❌ アプリは逆コンパイル可能
- ❌ ユーザーがアプリを更新しない

**BFFパターンの解決策**:
```
モバイルアプリ
    ↓ セッションCookie（HttpOnly）
BFF（Ktorアプリ）
    ↓ Access Token + Refresh Token
OAuth Provider（Cognito, Auth0, Okta）
    ↓ Access Token
バックエンドサービス
```

**メリット**:
- ✅ トークンはサーバーサイドで管理
- ✅ モバイルアプリは安全なCookieのみ保持
- ✅ トークンリフレッシュを透過的に処理

---

### 4.3 Authorization Code + PKCEフロー

#### PKCEとは

**PKCE（Proof Key for Code Exchange）**: モバイルアプリ向けのOAuth拡張

**従来の問題**: クライアントシークレットをモバイルアプリに埋め込めない

**PKCEの解決策**: code_verifierとcode_challengeで認証コード交換を保護

#### フローの詳細

```
1. モバイルアプリ: code_verifierを生成（ランダム文字列）
2. モバイルアプリ: code_challenge = SHA256(code_verifier)
3. モバイルアプリ → BFF: /auth/login
4. BFF → OAuth Provider: 認証リクエスト（code_challenge含む）
5. OAuth Provider → ユーザー: ログイン画面
6. ユーザー → OAuth Provider: ログイン
7. OAuth Provider → BFF: 認証コード
8. BFF → OAuth Provider: トークン交換（code_verifier含む）
9. OAuth Provider → BFF: Access Token + Refresh Token
10. BFF: トークンをサーバーサイドに保存
11. BFF → モバイルアプリ: セッションCookie
```

---

### 4.4 JWT（JSON Web Token）

#### JWTの構造

JWT = Header + Payload + Signature

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.  ← Header
eyJ1c2VyX2lkIjoiMTIzIiwibmFtZSI6IkpvaG4ifQ.  ← Payload
SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c  ← Signature
```

**Header（ヘッダー）**:
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload（ペイロード）**:
```json
{
  "user_id": "123",
  "email": "john@example.com",
  "exp": 1699000000,  // 有効期限
  "iat": 1698996400   // 発行時刻
}
```

**Signature（署名）**:
```
HMACSHA256(
  base64UrlEncode(header) + "." +
  base64UrlEncode(payload),
  secret
)
```

#### 主なクレーム（Claim）

| クレーム | 説明 | 例 |
|---------|------|-----|
| `sub` | Subject（ユーザーID） | `"user123"` |
| `iss` | Issuer（発行者） | `"https://auth.example.com"` |
| `aud` | Audience（対象） | `"api.example.com"` |
| `exp` | Expiration（有効期限） | `1699000000` |
| `iat` | Issued At（発行時刻） | `1698996400` |
| `nbf` | Not Before（有効開始） | `1698996400` |

---

### 4.5 Ktor Authenticationプラグイン

#### JWT認証の設定

**依存関係**:
```kotlin
// build.gradle.kts
dependencies {
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
}
```

**JWT設定**:
```kotlin
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWKProvider
import java.net.URL

fun Application.configureSecurity() {
    val jwksUrl = environment.config.property("jwt.jwksUrl").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "ktor-bff"
            
            // JWK Provider（公開鍵を取得）
            val jwkProvider = JwkProviderBuilder(URL(jwksUrl))
                .cached(10, 24, TimeUnit.HOURS)  // キャッシュ
                .rateLimited(10, 1, TimeUnit.MINUTES)  // レート制限
                .build()
            
            verifier(jwkProvider, issuer) {
                acceptLeeway(3)  // 3秒の時刻ずれを許容
                withAudience(audience)
            }
            
            validate { credential ->
                // トークンの検証
                val userId = credential.payload.getClaim("user_id").asString()
                if (userId.isNotEmpty()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            
            challenge { defaultScheme, realm ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Token is not valid or has expired")
                )
            }
        }
    }
}
```

#### 認証が必要なルートの保護

```kotlin
routing {
    // 認証不要なエンドポイント
    get("/health") {
        call.respondText("OK")
    }
    
    // 認証が必要なエンドポイント
    authenticate("auth-jwt") {
        get("/api/profile") {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.payload.getClaim("user_id").asString()
            val email = principal.payload.getClaim("email").asString()
            
            call.respond(mapOf(
                "userId" to userId,
                "email" to email
            ))
        }
        
        get("/api/users") {
            val principal = call.principal<JWTPrincipal>()!!
            // ...
        }
    }
}
```

---

### 4.6 セッション管理

#### Ktor Sessionsプラグイン

```kotlin
import io.ktor.server.sessions.*

@Serializable
data class UserSession(
    val sessionId: String,
    val userId: String
)

fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>("SESSION") {
            cookie.path = "/"
            cookie.httpOnly = true  // JavaScriptからアクセス不可
            cookie.secure = true    // HTTPS通信のみ
            cookie.extensions["SameSite"] = "Strict"  // CSRF対策
            cookie.maxAgeInSeconds = 3600  // 1時間
        }
    }
}
```

#### OAuth認証フロー

```kotlin
routing {
    // ログイン開始
    get("/auth/login") {
        // OAuth ProviderのURLにリダイレクト
        val authUrl = buildAuthorizationUrl(
            clientId = clientId,
            redirectUri = "$baseUrl/auth/callback",
            scope = "openid profile email",
            state = generateState()
        )
        call.respondRedirect(authUrl)
    }
    
    // コールバック
    get("/auth/callback") {
        val code = call.request.queryParameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest)
        
        // トークン交換
        val tokens = exchangeCodeForTokens(
            code = code,
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = "$baseUrl/auth/callback"
        )
        
        // トークンをサーバーサイドに保存
        val sessionId = UUID.randomUUID().toString()
        tokenStore.save(sessionId, TokenPair(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAt = Instant.now().plusSeconds(tokens.expiresIn)
        ))
        
        // JWTからユーザーIDを取得
        val jwt = JWT.decode(tokens.accessToken)
        val userId = jwt.getClaim("sub").asString()
        
        // セッションCookieを発行
        call.sessions.set(UserSession(sessionId, userId))
        
        call.respondRedirect("/")
    }
    
    // ログアウト
    get("/auth/logout") {
        val session = call.sessions.get<UserSession>()
        if (session != null) {
            tokenStore.delete(session.sessionId)
        }
        call.sessions.clear<UserSession>()
        call.respondRedirect("/")
    }
}
```

---

### 4.7 トークンリフレッシュ

#### なぜトークンリフレッシュが必要か

**Access Token**: 短命（15-60分）
- APIアクセスに使用
- 漏洩時の被害を最小化

**Refresh Token**: 長命（日〜週）
- Access Tokenの再発行に使用
- より厳重に管理

#### リフレッシュの実装

```kotlin
suspend fun refreshAccessToken(sessionId: String): String? {
    val tokenPair = tokenStore.get(sessionId) ?: return null
    
    // Access Tokenが期限切れか確認
    if (Instant.now() < tokenPair.expiresAt) {
        return tokenPair.accessToken  // まだ有効
    }
    
    // Refresh Tokenで新しいAccess Tokenを取得
    val newTokens = try {
        oauthClient.refreshToken(tokenPair.refreshToken)
    } catch (e: Exception) {
        logger.error("Failed to refresh token", e)
        tokenStore.delete(sessionId)
        return null
    }
    
    // 新しいトークンを保存
    tokenStore.save(sessionId, TokenPair(
        accessToken = newTokens.accessToken,
        refreshToken = newTokens.refreshToken,
        expiresAt = Instant.now().plusSeconds(newTokens.expiresIn)
    ))
    
    return newTokens.accessToken
}
```

#### インターセプターでの自動リフレッシュ

```kotlin
install(createApplicationPlugin(name = "TokenRefresh") {
    onCall { call ->
        val session = call.sessions.get<UserSession>() ?: return@onCall
        
        // トークンリフレッシュ
        val accessToken = refreshAccessToken(session.sessionId)
        if (accessToken == null) {
            // リフレッシュ失敗→ログアウト
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.Unauthorized)
            return@onCall
        }
        
        // リクエストにトークンを追加
        call.attributes.put(AccessTokenKey, accessToken)
    }
})
```

---

### 4.8 AWS Cognitoとの統合

#### Cognito設定

**JWKSエンドポイント**:
```
https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json
```

**application.conf**:
```hocon
jwt {
    jwksUrl = "https://cognito-idp.ap-northeast-1.amazonaws.com/ap-northeast-1_ABC123/.well-known/jwks.json"
    issuer = "https://cognito-idp.ap-northeast-1.amazonaws.com/ap-northeast-1_ABC123"
    audience = "your-client-id"
}
```

#### Cognito統合例

```kotlin
fun Application.configureCognitoAuth() {
    val region = "ap-northeast-1"
    val userPoolId = "ap-northeast-1_ABC123"
    val clientId = environment.config.property("cognito.clientId").getString()
    
    install(Authentication) {
        jwt("cognito") {
            realm = "ktor-bff"
            
            val jwksUrl = "https://cognito-idp.$region.amazonaws.com/$userPoolId/.well-known/jwks.json"
            val issuer = "https://cognito-idp.$region.amazonaws.com/$userPoolId"
            
            val jwkProvider = JwkProviderBuilder(URL(jwksUrl))
                .cached(10, 24, TimeUnit.HOURS)
                .build()
            
            verifier(jwkProvider, issuer) {
                acceptLeeway(3)
                withAudience(clientId)
            }
            
            validate { credential ->
                val username = credential.payload.getClaim("cognito:username").asString()
                if (username.isNotEmpty()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
```

---

### 4.9 API Key認証

#### API Key認証とは

**API Key**: サービスやクライアントを識別するための秘密の文字列

**特徴**:
- シンプルで実装が容易
- ユーザー認証ではなく、アプリケーション認証に適している
- 長期間有効なトークン

**使用例**:
```
Authorization: Bearer sk_live_1234567890abcdef
X-API-Key: your-api-key-here
```

---

#### API Key認証の使用ケース

**適している場面**:
- ✅ サーバー間通信（Server-to-Server）
- ✅ サードパーティAPIへのアクセス
- ✅ 内部マイクロサービス間の認証
- ✅ 管理用APIやバックオフィスツール
- ✅ Webhookエンドポイントの保護

**適していない場面**:
- ❌ エンドユーザーのログイン（OAuth/JWTを使うべき）
- ❌ 細かい権限制御が必要な場合
- ❌ 高セキュリティが求められる金融系アプリ

**モバイルBFFでの使用例**:
```
モバイルアプリ
    ↓ OAuth/JWT（ユーザー認証）
BFF（Ktorアプリ）
    ↓ API Key（アプリケーション認証）
バックエンドサービス（決済API、通知サービスなど）
```

---

#### API Key認証の実装（Ktor）

**カスタム認証プロバイダー**:
```kotlin
import io.ktor.server.auth.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.http.*

data class ApiKeyPrincipal(val apiKey: String) : Principal

fun Application.configureApiKeyAuth() {
    install(Authentication) {
        apiKey("api-key-auth") {
            validate { credential ->
                // データベースまたは設定からAPI Keyを検証
                if (isValidApiKey(credential)) {
                    ApiKeyPrincipal(credential)
                } else {
                    null
                }
            }
        }
    }
}

// カスタム認証プロバイダーの定義
fun AuthenticationConfig.apiKey(
    name: String,
    configure: ApiKeyAuthenticationProvider.Config.() -> Unit
) {
    val provider = ApiKeyAuthenticationProvider.Config(name).apply(configure).build()
    register(provider)
}

class ApiKeyAuthenticationProvider(config: Config) : AuthenticationProvider(config) {
    private val validate = config.validate

    class Config(name: String) : AuthenticationProvider.Config(name) {
        var validate: suspend (String) -> Principal? = { null }

        fun build() = ApiKeyAuthenticationProvider(this)
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val apiKey = call.request.headers["X-API-Key"]
            ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")

        val principal = apiKey?.let { validate(it) }

        if (principal != null) {
            context.principal(principal)
        } else {
            context.challenge("ApiKeyAuth", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid or missing API key")
                )
                challenge.complete()
            }
        }
    }
}
```

**API Keyの検証**:
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

suspend fun isValidApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
    // オプション1: 環境変数から検証
    val validKeys = System.getenv("VALID_API_KEYS")?.split(",") ?: emptyList()
    if (validKeys.contains(apiKey)) {
        return@withContext true
    }

    // オプション2: データベースから検証
    val hashedKey = hashApiKey(apiKey)
    apiKeyRepository.exists(hashedKey)
}

fun hashApiKey(apiKey: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(apiKey.toByteArray())
    return hash.joinToString("") { "%02x".format(it) }
}
```

**保護されたルート**:
```kotlin
routing {
    authenticate("api-key-auth") {
        get("/api/internal/metrics") {
            val principal = call.principal<ApiKeyPrincipal>()!!
            call.respond(mapOf(
                "authenticatedWith" to principal.apiKey.take(10) + "...",
                "metrics" to getMetrics()
            ))
        }

        post("/api/webhooks/payment") {
            // 外部サービスからのWebhookを受信
            val payload = call.receive<PaymentWebhook>()
            processPayment(payload)
            call.respond(HttpStatusCode.OK)
        }
    }
}
```

---

#### 複数の認証方式の併用

**JWT + API Keyの組み合わせ**:
```kotlin
fun Application.configureMultipleAuth() {
    install(Authentication) {
        // JWT認証（モバイルアプリ向け）
        jwt("user-jwt") {
            // ... JWT設定
        }

        // API Key認証（サーバー間通信向け）
        apiKey("service-api-key") {
            validate { credential ->
                if (isValidApiKey(credential)) {
                    ApiKeyPrincipal(credential)
                } else {
                    null
                }
            }
        }
    }
}

routing {
    // ユーザー向けエンドポイント（JWT認証）
    authenticate("user-jwt") {
        get("/api/user/profile") {
            val principal = call.principal<JWTPrincipal>()!!
            call.respond(getUserProfile(principal))
        }
    }

    // 内部サービス向けエンドポイント（API Key認証）
    authenticate("service-api-key") {
        get("/api/internal/users") {
            call.respond(getAllUsers())
        }
    }

    // どちらかの認証方式でアクセス可能
    authenticate("user-jwt", "service-api-key", optional = false) {
        get("/api/data") {
            val userPrincipal = call.principal<JWTPrincipal>()
            val apiKeyPrincipal = call.principal<ApiKeyPrincipal>()

            when {
                userPrincipal != null -> call.respond(getUserData(userPrincipal))
                apiKeyPrincipal != null -> call.respond(getServiceData())
                else -> call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}
```

---

#### API Keyのセキュリティベストプラクティス

**1. API Keyの保存場所**

```kotlin
// ✅ 良い例：環境変数
val apiKey = System.getenv("THIRD_PARTY_API_KEY")

// ✅ 良い例：AWS Secrets Manager
suspend fun getApiKey(): String {
    val secretsManagerClient = SecretsManagerClient { region = "ap-northeast-1" }
    val response = secretsManagerClient.getSecretValue {
        secretId = "prod/third-party-api-key"
    }
    return response.secretString!!
}

// ❌ 悪い例：ハードコード
val apiKey = "sk_live_1234567890abcdef"  // 絶対にやらない！
```

**2. 送信方法**

```kotlin
// ✅ 推奨：Authorizationヘッダー
client.get("https://api.example.com/data") {
    header("Authorization", "Bearer $apiKey")
}

// ✅ 代替：カスタムヘッダー
client.get("https://api.example.com/data") {
    header("X-API-Key", apiKey)
}

// ❌ 非推奨：クエリパラメータ（ログに残りやすい）
client.get("https://api.example.com/data?api_key=$apiKey")
```

**3. API Keyのハッシュ化保存**

```kotlin
// データベーススキーマ
@Serializable
data class ApiKeyRecord(
    val id: String,
    val name: String,
    val hashedKey: String,  // ハッシュ化されたキー
    val prefix: String,      // 先頭8文字（識別用）
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val expiresAt: Instant?
)

// API Key生成
suspend fun generateApiKey(name: String): Pair<String, ApiKeyRecord> {
    val apiKey = "sk_live_${generateSecureRandomString(32)}"
    val hashedKey = hashApiKey(apiKey)
    val prefix = apiKey.take(8)

    val record = ApiKeyRecord(
        id = UUID.randomUUID().toString(),
        name = name,
        hashedKey = hashedKey,
        prefix = prefix,
        createdAt = Instant.now(),
        lastUsedAt = null,
        expiresAt = Instant.now().plus(365, ChronoUnit.DAYS)
    )

    apiKeyRepository.save(record)

    // 生成されたキーは一度だけ返す
    return apiKey to record
}

fun generateSecureRandomString(length: Int): String {
    val bytes = ByteArray(length)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
```

**4. API Keyのローテーション**

```kotlin
data class ApiKeyWithExpiry(
    val key: String,
    val expiresAt: Instant
)

suspend fun rotateApiKey(oldKeyId: String): ApiKeyWithExpiry {
    // 新しいキーを生成
    val (newKey, newRecord) = generateApiKey("rotated-key")

    // 古いキーに猶予期間を設定（30日後に無効化）
    apiKeyRepository.update(oldKeyId) {
        it.copy(expiresAt = Instant.now().plus(30, ChronoUnit.DAYS))
    }

    return ApiKeyWithExpiry(newKey, newRecord.expiresAt!!)
}
```

**5. レート制限**

```kotlin
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

fun Application.configureApiKeyRateLimit() {
    install(RateLimit) {
        register(RateLimitName("api-key-limit")) {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
            requestKey { call ->
                call.principal<ApiKeyPrincipal>()?.apiKey ?: "unknown"
            }
        }
    }
}

routing {
    rateLimit(RateLimitName("api-key-limit")) {
        authenticate("api-key-auth") {
            get("/api/internal/data") {
                call.respond(getData())
            }
        }
    }
}
```

**6. 監査ログ**

```kotlin
install(createApplicationPlugin(name = "ApiKeyAudit") {
    onCall { call ->
        val apiKeyPrincipal = call.principal<ApiKeyPrincipal>()
        if (apiKeyPrincipal != null) {
            // 使用記録
            auditLogger.info {
                mapOf(
                    "apiKeyPrefix" to apiKeyPrincipal.apiKey.take(8),
                    "endpoint" to call.request.uri,
                    "method" to call.request.httpMethod.value,
                    "timestamp" to Instant.now()
                )
            }

            // 最終使用時刻を更新
            apiKeyRepository.updateLastUsed(apiKeyPrincipal.apiKey)
        }
    }
})
```

---

#### API Keyの管理API例

```kotlin
// 管理者用API
authenticate("admin-jwt") {
    // API Keyの一覧表示
    get("/admin/api-keys") {
        val keys = apiKeyRepository.listAll()
        call.respond(keys.map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "prefix" to it.prefix,
                "createdAt" to it.createdAt,
                "lastUsedAt" to it.lastUsedAt,
                "expiresAt" to it.expiresAt
            )
        })
    }

    // API Keyの生成
    post("/admin/api-keys") {
        val request = call.receive<CreateApiKeyRequest>()
        val (apiKey, record) = generateApiKey(request.name)

        call.respond(HttpStatusCode.Created, mapOf(
            "apiKey" to apiKey,  // 一度だけ表示
            "id" to record.id,
            "prefix" to record.prefix,
            "expiresAt" to record.expiresAt
        ))
    }

    // API Keyの無効化
    delete("/admin/api-keys/{id}") {
        val id = call.parameters["id"]!!
        apiKeyRepository.delete(id)
        call.respond(HttpStatusCode.NoContent)
    }

    // API Keyのローテーション
    post("/admin/api-keys/{id}/rotate") {
        val id = call.parameters["id"]!!
        val newKey = rotateApiKey(id)
        call.respond(mapOf(
            "newApiKey" to newKey.key,
            "expiresAt" to newKey.expiresAt,
            "gracePeriodDays" to 30
        ))
    }
}

@Serializable
data class CreateApiKeyRequest(
    val name: String
)
```

---

#### まとめ：API Key認証

**API Key認証が適している場面**:
- サーバー間通信
- サードパーティAPIアクセス
- 内部ツール・管理用API
- Webhookエンドポイント

**セキュリティのポイント**:
1. ✅ API Keyを環境変数やSecrets Managerで管理
2. ✅ データベースにはハッシュ化して保存
3. ✅ Authorizationヘッダーで送信
4. ✅ 定期的にローテーション
5. ✅ レート制限を設定
6. ✅ 使用状況を監査ログに記録

**OAuth/JWTとの使い分け**:
- **OAuth/JWT**: エンドユーザーの認証・認可
- **API Key**: アプリケーション・サービスの認証

---

### まとめ

この章で学んだこと:

1. ✅ **認証と認可の違い**
2. ✅ **モバイルBFFでの認証フロー**
3. ✅ **Authorization Code + PKCEフロー**
4. ✅ **JWT構造と検証**
5. ✅ **Ktor Authentication プラグイン**
6. ✅ **セッション管理**
7. ✅ **トークンリフレッシュ**
8. ✅ **AWS Cognito統合**
9. ✅ **API Key認証とセキュリティベストプラクティス**

---

### 次のステップ

次は**Part 5: バックエンドAPI統合**で、HTTPクライアントとAPI集約を学びます。

---

### 学習チェックリスト

- [ ] 認証と認可の違いを説明できる
- [ ] なぜBFFでトークンを管理するのか理解している
- [ ] PKCEフローを説明できる
- [ ] JWTの構造を理解している
- [ ] Ktor AuthenticationプラグインでJWT認証を実装できる
- [ ] セッションCookieを使用できる
- [ ] トークンリフレッシュを実装できる
- [ ] AWS Cognitoと統合できる
- [ ] API Key認証の使用ケースを理解している
- [ ] API Keyのセキュリティベストプラクティスを実践できる
- [ ] 複数の認証方式を併用できる

全てチェックできたら、次のPartに進みましょう！
