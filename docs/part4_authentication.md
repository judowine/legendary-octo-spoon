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

全てチェックできたら、次のPartに進みましょう！
