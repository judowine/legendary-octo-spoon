# Part 5: バックエンドAPI統合（Ktor HTTPクライアント）

## Chapter 5: Ktor Clientの使い方

### 5.1 なぜHTTPクライアントが必要か

#### BFFパターンの核心

モバイルBFF（Backend for Frontend）の最大の価値は、**複数のバックエンドサービスを集約して、モバイルに最適化されたAPIを提供すること**です。

**BFFなしの場合**:
```
モバイルアプリ
  ↓ HTTPリクエスト1: ユーザー情報取得
User Service
  ↓ HTTPレスポンス1
モバイルアプリ
  ↓ HTTPリクエスト2: 注文履歴取得
Order Service
  ↓ HTTPレスポンス2
モバイルアプリ
  ↓ HTTPリクエスト3: おすすめ商品取得
Recommendation Service
  ↓ HTTPレスポンス3
モバイルアプリ

→ 3回のラウンドトリップ、遅い、データ量が多い
```

**BFFありの場合**:
```
モバイルアプリ
  ↓ HTTPリクエスト: /mobile/v1/home
BFF (Ktorアプリ)
  ├→ User Service（並列）
  ├→ Order Service（並列）
  └→ Recommendation Service（並列）
  ↓ 集約・変換・最適化
モバイルアプリ
  ↓ HTTPレスポンス: 1つの最適化されたJSON

→ 1回のラウンドトリップ、速い、データ量が少ない
```

**メリット**:
- ✅ **レイテンシの削減**: 複数リクエストを1つに集約
- ✅ **データ転送量の削減**: 不要なフィールドを除去、70-90%削減可能
- ✅ **モバイル最適化**: モバイルUIに必要なデータだけを返す
- ✅ **バックエンドの変更を隠蔽**: バックエンドが変わってもモバイルアプリに影響しない
- ✅ **認証の一元化**: トークン管理をサーバーサイドで行う

---

### 5.2 Ktor HTTPクライアントのセットアップ

#### 依存関係の追加

```kotlin
// build.gradle.kts
dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    
    // Ktor Client
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")  // エンジン
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-client-auth")
    
    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
}
```

#### エンジンの選択

Ktor Clientは複数のエンジンをサポートしています。

| エンジン | 特徴 | 推奨用途 |
|---------|------|---------|
| **CIO** | Kotlin純正、軽量、コルーチンネイティブ | 小〜中規模、開発環境 |
| **Apache** | 成熟、高機能、HTTP/2対応 | 本番環境、大規模 |
| **OkHttp** | Android由来、信頼性高い | 本番環境、モバイル親和性 |
| **Java** | Java 11+ HttpClient使用 | Java環境との統合 |

**推奨**: 開発時は**CIO**、本番環境では**Apache**または**OkHttp**

#### 基本的なHTTPクライアントの作成

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

val client = HttpClient(CIO) {
    // Content Negotiation（JSON処理）
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true  // バックエンドが新しいフィールドを追加しても動作
        })
    }
    
    // Logging（開発時のデバッグ用）
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.INFO
        filter { request ->
            request.url.host.contains("api.example.com")
        }
    }
    
    // Engine設定
    engine {
        maxConnectionsCount = 100  // 最大接続数
        endpoint {
            maxConnectionsPerRoute = 20  // ホストあたりの最大接続数
            pipelineMaxSize = 20  // パイプライン最大サイズ
            keepAliveTime = 5000  // Keep-Alive時間（ミリ秒）
            connectTimeout = 5000  // 接続タイムアウト
            connectAttempts = 5  // 接続リトライ回数
        }
    }
}
```

#### HttpClientを依存性注入で管理

**重要**: HTTPクライアントは**アプリケーション全体で1つのインスタンスを共有**します。リクエストごとに作成すると、コネクションプールが効かずパフォーマンスが劣化します。

```kotlin
import io.ktor.server.application.*

fun Application.configureHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        
        install(Logging) {
            level = LogLevel.INFO
        }
        
        engine {
            maxConnectionsCount = 100
            endpoint {
                maxConnectionsPerRoute = 20
                connectTimeout = 5000
            }
        }
    }
}

fun Application.module() {
    // HTTPクライアントを1つ作成
    val httpClient = configureHttpClient()
    
    // 依存性注入（Koin等を使う場合）
    // または、単純にルーティングに渡す
    configureRouting(httpClient)
    
    // アプリケーション終了時にクライアントをクローズ
    environment.monitor.subscribe(ApplicationStopped) {
        httpClient.close()
    }
}
```

---

### 5.3 GETリクエスト - データの取得

#### 基本的なGETリクエスト

```kotlin
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String
)

suspend fun getUser(client: HttpClient, userId: String): User {
    return client.get("https://api.example.com/users/$userId") {
        // ヘッダーを追加
        header("Authorization", "Bearer $accessToken")
        header("Accept", "application/json")
    }.body()  // 自動的にUserにデシリアライズ
}
```

#### クエリパラメータ付きGETリクエスト

```kotlin
@Serializable
data class Order(
    val id: String,
    val userId: String,
    val productName: String,
    val quantity: Int,
    val createdAt: String
)

@Serializable
data class OrdersResponse(
    val orders: List<Order>,
    val total: Int,
    val page: Int
)

suspend fun getOrders(
    client: HttpClient,
    userId: String,
    page: Int = 1,
    limit: Int = 10
): OrdersResponse {
    return client.get("https://api.example.com/orders") {
        header("Authorization", "Bearer $accessToken")
        
        // クエリパラメータ
        parameter("userId", userId)
        parameter("page", page)
        parameter("limit", limit)
        parameter("sort", "createdAt:desc")
    }.body()
}
```

#### レスポンスの型安全な処理

```kotlin
import io.ktor.client.statement.*
import io.ktor.http.*

suspend fun getUserSafe(client: HttpClient, userId: String): User? {
    val response: HttpResponse = client.get("https://api.example.com/users/$userId") {
        header("Authorization", "Bearer $accessToken")
    }
    
    return when (response.status) {
        HttpStatusCode.OK -> response.body<User>()
        HttpStatusCode.NotFound -> null
        else -> throw Exception("Failed to get user: ${response.status}")
    }
}
```

---

### 5.4 POSTリクエスト - データの作成

#### JSON ボディ付きPOSTリクエスト

```kotlin
import io.ktor.client.request.*
import io.ktor.http.*

@Serializable
data class CreateOrderRequest(
    val userId: String,
    val productId: String,
    val quantity: Int
)

@Serializable
data class OrderResponse(
    val id: String,
    val userId: String,
    val productName: String,
    val quantity: Int,
    val total: Double,
    val status: String
)

suspend fun createOrder(
    client: HttpClient,
    request: CreateOrderRequest
): OrderResponse {
    return client.post("https://api.example.com/orders") {
        header("Authorization", "Bearer $accessToken")
        contentType(ContentType.Application.Json)
        setBody(request)  // 自動的にJSONにシリアライズ
    }.body()
}
```

#### フォームデータの送信

```kotlin
import io.ktor.client.request.forms.*
import io.ktor.http.content.*

suspend fun uploadFile(client: HttpClient, file: ByteArray, fileName: String): String {
    return client.submitFormWithBinaryData(
        url = "https://api.example.com/upload",
        formData = formData {
            append("file", file, Headers.build {
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
            })
            append("description", "User uploaded image")
        }
    ) {
        header("Authorization", "Bearer $accessToken")
    }.body()
}
```

---

### 5.5 PUT/PATCH/DELETEリクエスト

#### PUTリクエスト - 完全更新

```kotlin
@Serializable
data class UpdateUserRequest(
    val name: String,
    val email: String,
    val phone: String
)

suspend fun updateUser(
    client: HttpClient,
    userId: String,
    request: UpdateUserRequest
): User {
    return client.put("https://api.example.com/users/$userId") {
        header("Authorization", "Bearer $accessToken")
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()
}
```

#### PATCHリクエスト - 部分更新

```kotlin
@Serializable
data class PatchUserRequest(
    val email: String? = null,
    val phone: String? = null
)

suspend fun patchUser(
    client: HttpClient,
    userId: String,
    request: PatchUserRequest
): User {
    return client.patch("https://api.example.com/users/$userId") {
        header("Authorization", "Bearer $accessToken")
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()
}
```

#### DELETEリクエスト

```kotlin
suspend fun deleteOrder(client: HttpClient, orderId: String): Boolean {
    val response: HttpResponse = client.delete("https://api.example.com/orders/$orderId") {
        header("Authorization", "Bearer $accessToken")
    }
    
    return response.status == HttpStatusCode.NoContent ||
           response.status == HttpStatusCode.OK
}
```

---

### 5.6 複数APIの並列呼び出し（BFFの核心）

#### async/awaitでの並列実行

```kotlin
import kotlinx.coroutines.*

@Serializable
data class HomeData(
    val user: User,
    val recentOrders: List<Order>,
    val recommendations: List<Product>
)

@Serializable
data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val imageUrl: String
)

suspend fun getHomeData(
    client: HttpClient,
    userId: String
): HomeData = coroutineScope {
    // 3つのAPIを並列で呼び出す
    val userDeferred = async {
        client.get("https://user-service.com/users/$userId") {
            header("Authorization", "Bearer $accessToken")
        }.body<User>()
    }
    
    val ordersDeferred = async {
        client.get("https://order-service.com/orders") {
            header("Authorization", "Bearer $accessToken")
            parameter("userId", userId)
            parameter("limit", 5)
            parameter("sort", "createdAt:desc")
        }.body<List<Order>>()
    }
    
    val recommendationsDeferred = async {
        client.get("https://recommendation-service.com/recommendations/$userId") {
            header("Authorization", "Bearer $accessToken")
            parameter("limit", 10)
        }.body<List<Product>>()
    }
    
    // 全ての結果を待つ
    HomeData(
        user = userDeferred.await(),
        recentOrders = ordersDeferred.await(),
        recommendations = recommendationsDeferred.await()
    )
}
```

**重要ポイント**:
- ✅ `coroutineScope`を使用（構造化並行性）
- ✅ `async`で各API呼び出しを開始
- ✅ `await()`で結果を取得
- ✅ いずれかのAPIが失敗すると、全体がキャンセルされる

#### 並列実行の効果

**シーケンシャル実行**:
```
User API: 200ms
  ↓ 待つ
Order API: 150ms
  ↓ 待つ
Recommendation API: 180ms

合計: 530ms
```

**並列実行**:
```
User API: 200ms ┐
Order API: 150ms ├→ 並列実行
Recommendation API: 180ms ┘

合計: 200ms（最も遅いAPIの時間）
```

**レイテンシ削減**: 530ms → 200ms（62%削減）

---

### 5.7 部分的な失敗への対応

#### オプショナルなデータの扱い

モバイルアプリでは、**重要なデータ**（ユーザー情報）と**オプショナルなデータ**（レコメンデーション）を区別します。

```kotlin
suspend fun getHomeDataWithFallback(
    client: HttpClient,
    userId: String
): HomeData = coroutineScope {
    // 必須データ: 失敗したら全体が失敗
    val userDeferred = async {
        client.get("https://user-service.com/users/$userId") {
            header("Authorization", "Bearer $accessToken")
        }.body<User>()
    }
    
    val ordersDeferred = async {
        client.get("https://order-service.com/orders") {
            header("Authorization", "Bearer $accessToken")
            parameter("userId", userId)
            parameter("limit", 5)
        }.body<List<Order>>()
    }
    
    // オプショナルデータ: 失敗しても空リストを返す
    val recommendationsDeferred = async {
        try {
            client.get("https://recommendation-service.com/recommendations/$userId") {
                header("Authorization", "Bearer $accessToken")
                parameter("limit", 10)
            }.body<List<Product>>()
        } catch (e: Exception) {
            logger.warn("Failed to get recommendations", e)
            emptyList<Product>()  // 失敗しても空リストを返す
        }
    }
    
    HomeData(
        user = userDeferred.await(),  // 失敗したら例外がスロー
        recentOrders = ordersDeferred.await(),  // 失敗したら例外がスロー
        recommendations = recommendationsDeferred.await()  // 失敗しても空リスト
    )
}
```

#### runCatchingでの安全な実行

```kotlin
suspend fun getHomeDataSafe(
    client: HttpClient,
    userId: String
): Result<HomeData> = runCatching {
    coroutineScope {
        val user = async {
            client.get("https://user-service.com/users/$userId") {
                header("Authorization", "Bearer $accessToken")
            }.body<User>()
        }
        
        val orders = async {
            runCatching {
                client.get("https://order-service.com/orders") {
                    header("Authorization", "Bearer $accessToken")
                    parameter("userId", userId)
                }.body<List<Order>>()
            }.getOrElse { emptyList() }
        }
        
        val recommendations = async {
            runCatching {
                client.get("https://recommendation-service.com/recommendations/$userId") {
                    header("Authorization", "Bearer $accessToken")
                }.body<List<Product>>()
            }.getOrElse { emptyList() }
        }
        
        HomeData(
            user = user.await(),
            recentOrders = orders.await(),
            recommendations = recommendations.await()
        )
    }
}

// 使用例
val result = getHomeDataSafe(client, userId)
when {
    result.isSuccess -> call.respond(result.getOrThrow())
    result.isFailure -> call.respond(
        HttpStatusCode.InternalServerError,
        ErrorResponse("Failed to get home data")
    )
}
```

---

### 5.8 タイムアウトの設定

#### HttpTimeoutプラグイン

```kotlin
import io.ktor.client.plugins.*

val client = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 15000  // リクエスト全体のタイムアウト
        connectTimeoutMillis = 5000   // 接続タイムアウト
        socketTimeoutMillis = 15000   // ソケットタイムアウト
    }
    
    install(ContentNegotiation) {
        json()
    }
}
```

#### リクエストごとのタイムアウト

```kotlin
suspend fun getUserWithTimeout(client: HttpClient, userId: String): User {
    return client.get("https://api.example.com/users/$userId") {
        header("Authorization", "Bearer $accessToken")
        
        // このリクエストだけ短いタイムアウト
        timeout {
            requestTimeoutMillis = 5000
            connectTimeoutMillis = 2000
        }
    }.body()
}
```

#### タイムアウトのベストプラクティス

| 設定 | 推奨値 | 理由 |
|-----|-------|------|
| **connectTimeoutMillis** | 5秒 | サーバーが応答しない場合に早く失敗 |
| **requestTimeoutMillis** | 10-15秒 | 通常のAPIレスポンス時間 |
| **socketTimeoutMillis** | 15-20秒 | データ転送が止まった場合の検出 |

**モバイルBFF向けのタイムアウト戦略**:
```kotlin
val fastClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 5000  // 高速なサービス用
        connectTimeoutMillis = 2000
    }
}

val slowClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 30000  // 遅いサービス用（レポート生成等）
        connectTimeoutMillis = 5000
    }
}
```

---

### 5.9 認証トークンの伝播

#### Bearerトークンの自動追加

```kotlin
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*

val client = HttpClient(CIO) {
    install(Auth) {
        bearer {
            loadTokens {
                // トークンを取得（データベースやキャッシュから）
                BearerTokens(
                    accessToken = getAccessToken(),
                    refreshToken = getRefreshToken()
                )
            }
            
            refreshTokens {
                // トークンリフレッシュ
                val newTokens = refreshAccessToken(oldTokens?.refreshToken)
                BearerTokens(
                    accessToken = newTokens.accessToken,
                    refreshToken = newTokens.refreshToken
                )
            }
        }
    }
    
    install(ContentNegotiation) {
        json()
    }
}
```

#### DefaultRequestプラグイン

```kotlin
import io.ktor.client.plugins.*

val client = HttpClient(CIO) {
    install(DefaultRequest) {
        // 全てのリクエストに自動追加
        header("Authorization", "Bearer $accessToken")
        header("User-Agent", "Ktor-BFF/1.0")
        header("Accept", "application/json")
        
        // ベースURL
        url("https://api.example.com/")
    }
    
    install(ContentNegotiation) {
        json()
    }
}

// 使用時はパスだけ指定
val user = client.get("users/$userId").body<User>()
```

#### サービスごとのクライアント

```kotlin
class UserServiceClient(private val accessToken: String) {
    private val client = HttpClient(CIO) {
        install(DefaultRequest) {
            url("https://user-service.com/")
            header("Authorization", "Bearer $accessToken")
        }
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
        }
    }
    
    suspend fun getUser(userId: String): User {
        return client.get("users/$userId").body()
    }
    
    suspend fun updateUser(userId: String, request: UpdateUserRequest): User {
        return client.put("users/$userId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    fun close() {
        client.close()
    }
}

class OrderServiceClient(private val accessToken: String) {
    private val client = HttpClient(CIO) {
        install(DefaultRequest) {
            url("https://order-service.com/")
            header("Authorization", "Bearer $accessToken")
        }
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
        }
    }
    
    suspend fun getOrders(userId: String, limit: Int = 10): List<Order> {
        return client.get("orders") {
            parameter("userId", userId)
            parameter("limit", limit)
        }.body()
    }
    
    suspend fun createOrder(request: CreateOrderRequest): OrderResponse {
        return client.post("orders") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    fun close() {
        client.close()
    }
}
```

---

### 5.10 エラーハンドリング

#### HTTPステータスコードでの判定

```kotlin
import io.ktor.client.statement.*
import io.ktor.http.*

suspend fun getUserWithErrorHandling(client: HttpClient, userId: String): User {
    val response: HttpResponse = client.get("https://api.example.com/users/$userId") {
        header("Authorization", "Bearer $accessToken")
    }
    
    return when (response.status) {
        HttpStatusCode.OK -> response.body<User>()
        
        HttpStatusCode.NotFound -> throw NotFoundException("User not found: $userId")
        
        HttpStatusCode.Unauthorized -> throw UnauthorizedException("Invalid token")
        
        HttpStatusCode.Forbidden -> throw ForbiddenException("Access denied")
        
        HttpStatusCode.BadRequest -> {
            val error = response.body<ErrorResponse>()
            throw BadRequestException(error.message)
        }
        
        HttpStatusCode.InternalServerError -> {
            throw BackendException("Backend service error")
        }
        
        else -> throw Exception("Unexpected status: ${response.status}")
    }
}
```

#### 例外の定義

```kotlin
// カスタム例外
sealed class ApiException(message: String) : Exception(message)

class NotFoundException(message: String) : ApiException(message)
class UnauthorizedException(message: String) : ApiException(message)
class ForbiddenException(message: String) : ApiException(message)
class BadRequestException(message: String) : ApiException(message)
class BackendException(message: String) : ApiException(message)
class TimeoutException(message: String) : ApiException(message)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)
```

#### StatusPagesでのグローバルエラーハンドリング

```kotlin
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("NOT_FOUND", cause.message ?: "Resource not found")
            )
        }
        
        exception<UnauthorizedException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("UNAUTHORIZED", cause.message ?: "Authentication required")
            )
        }
        
        exception<ForbiddenException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse("FORBIDDEN", cause.message ?: "Access denied")
            )
        }
        
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("BAD_REQUEST", cause.message ?: "Invalid request")
            )
        }
        
        exception<BackendException> { call, cause ->
            logger.error("Backend service error", cause)
            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse("BACKEND_ERROR", "Backend service unavailable")
            )
        }
        
        exception<TimeoutException> { call, cause ->
            logger.error("Request timeout", cause)
            call.respond(
                HttpStatusCode.GatewayTimeout,
                ErrorResponse("TIMEOUT", "Request timeout")
            )
        }
        
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")
            )
        }
    }
}
```

---

### 5.11 レスポンス変換（モバイル最適化）

#### バックエンドDTOからモバイルDTOへの変換

**バックエンドのレスポンス**（冗長）:
```kotlin
@Serializable
data class BackendUser(
    val id: String,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?,
    val dateOfBirth: String?,
    val address: Address?,
    val preferences: UserPreferences?,
    val metadata: Map<String, String>?,
    val createdAt: String,
    val updatedAt: String,
    val lastLoginAt: String?
)

@Serializable
data class Address(
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String
)

@Serializable
data class UserPreferences(
    val language: String,
    val timezone: String,
    val notificationsEnabled: Boolean,
    val marketingEmailsEnabled: Boolean
)
```

**モバイルアプリが必要なデータ**（簡潔）:
```kotlin
@Serializable
data class MobileUser(
    val id: String,
    val name: String,  // firstName + lastName
    val email: String,
    val avatarUrl: String  // 計算されたURL
)
```

**変換ロジック**:
```kotlin
fun BackendUser.toMobileUser(): MobileUser {
    return MobileUser(
        id = id,
        name = "$firstName $lastName",
        email = email,
        avatarUrl = "https://cdn.example.com/avatars/$id.jpg"
    )
}

// 使用例
suspend fun getMobileUser(client: HttpClient, userId: String): MobileUser {
    val backendUser = client.get("https://user-service.com/users/$userId") {
        header("Authorization", "Bearer $accessToken")
    }.body<BackendUser>()
    
    return backendUser.toMobileUser()
}
```

#### リストの変換

```kotlin
@Serializable
data class BackendProduct(
    val id: String,
    val name: String,
    val description: String,
    val longDescription: String,
    val price: Double,
    val currency: String,
    val images: List<ProductImage>,
    val specifications: Map<String, String>,
    val inventory: Inventory,
    val rating: Rating,
    val reviews: List<Review>,
    val relatedProducts: List<String>,
    val metadata: Map<String, String>
)

@Serializable
data class MobileProduct(
    val id: String,
    val name: String,
    val price: String,  // フォーマット済み
    val thumbnail: String,  // 最初の画像のサムネイル
    val inStock: Boolean  // 在庫の有無
)

fun BackendProduct.toMobileProduct(): MobileProduct {
    return MobileProduct(
        id = id,
        name = name,
        price = "$${String.format("%.2f", price)}",
        thumbnail = images.firstOrNull()?.thumbnailUrl ?: "",
        inStock = inventory.availableQuantity > 0
    )
}

// 使用例
suspend fun getMobileProducts(client: HttpClient): List<MobileProduct> {
    val backendProducts = client.get("https://product-service.com/products") {
        header("Authorization", "Bearer $accessToken")
        parameter("limit", 20)
    }.body<List<BackendProduct>>()
    
    return backendProducts.map { it.toMobileProduct() }
}
```

**データ削減の効果**:
- バックエンドレスポンス: 約5KB/商品
- モバイルレスポンス: 約0.5KB/商品
- **削減率: 90%**

---

### 5.12 実践例: ホーム画面API

#### 要件

モバイルアプリのホーム画面に表示するデータ:
1. ユーザー情報（名前、アバター）
2. 最近の注文（最新5件）
3. おすすめ商品（10件）

#### 実装

```kotlin
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*

@Serializable
data class MobileHomeResponse(
    val user: MobileUser,
    val recentOrders: List<MobileOrder>,
    val recommendations: List<MobileProduct>
)

@Serializable
data class MobileOrder(
    val id: String,
    val productName: String,
    val quantity: Int,
    val total: String,
    val status: String,
    val createdAt: String
)

// サービス層
class HomeService(
    private val userClient: UserServiceClient,
    private val orderClient: OrderServiceClient,
    private val recommendationClient: RecommendationServiceClient
) {
    suspend fun getHomeData(userId: String): MobileHomeResponse = coroutineScope {
        // 3つのAPIを並列呼び出し
        val userDeferred = async {
            try {
                val backendUser = userClient.getUser(userId)
                backendUser.toMobileUser()
            } catch (e: Exception) {
                logger.error("Failed to get user", e)
                throw e  // ユーザー情報は必須なので例外をスロー
            }
        }
        
        val ordersDeferred = async {
            try {
                val backendOrders = orderClient.getOrders(userId, limit = 5)
                backendOrders.map { it.toMobileOrder() }
            } catch (e: Exception) {
                logger.warn("Failed to get orders", e)
                emptyList()  // 注文は表示できなくてもOK
            }
        }
        
        val recommendationsDeferred = async {
            try {
                val backendProducts = recommendationClient.getRecommendations(userId, limit = 10)
                backendProducts.map { it.toMobileProduct() }
            } catch (e: Exception) {
                logger.warn("Failed to get recommendations", e)
                emptyList()  // レコメンデーションは表示できなくてもOK
            }
        }
        
        MobileHomeResponse(
            user = userDeferred.await(),
            recentOrders = ordersDeferred.await(),
            recommendations = recommendationsDeferred.await()
        )
    }
}

// ルーティング
fun Route.homeRoutes(homeService: HomeService) {
    authenticate("auth-jwt") {
        get("/mobile/v1/home") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            
            val userId = principal.payload.getClaim("user_id").asString()
            
            try {
                val homeData = homeService.getHomeData(userId)
                call.respond(homeData)
            } catch (e: Exception) {
                logger.error("Failed to get home data for user $userId", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("INTERNAL_ERROR", "Failed to load home data")
                )
            }
        }
    }
}
```

#### レスポンス例

```json
{
  "user": {
    "id": "user123",
    "name": "John Doe",
    "email": "john@example.com",
    "avatarUrl": "https://cdn.example.com/avatars/user123.jpg"
  },
  "recentOrders": [
    {
      "id": "order456",
      "productName": "Kotlin in Action",
      "quantity": 1,
      "total": "$39.99",
      "status": "delivered",
      "createdAt": "2025-11-01T10:30:00Z"
    },
    {
      "id": "order457",
      "productName": "Effective Java",
      "quantity": 2,
      "total": "$79.98",
      "status": "shipped",
      "createdAt": "2025-11-02T14:20:00Z"
    }
  ],
  "recommendations": [
    {
      "id": "prod789",
      "name": "Head First Design Patterns",
      "price": "$44.99",
      "thumbnail": "https://cdn.example.com/products/prod789_thumb.jpg",
      "inStock": true
    },
    {
      "id": "prod790",
      "name": "Clean Code",
      "price": "$49.99",
      "thumbnail": "https://cdn.example.com/products/prod790_thumb.jpg",
      "inStock": true
    }
  ]
}
```

---

### 5.13 パフォーマンス最適化

#### コネクションプールの調整

```kotlin
val client = HttpClient(CIO) {
    engine {
        maxConnectionsCount = 250  // 全体の最大接続数
        
        endpoint {
            maxConnectionsPerRoute = 100  // ホストあたりの最大接続数
            pipelineMaxSize = 20  // HTTP/1.1パイプライン
            keepAliveTime = 5000  // Keep-Alive時間（ミリ秒）
            connectTimeout = 5000
            connectAttempts = 5
        }
        
        // HTTP/2を有効化（Apache/OkHttpエンジンのみ）
        // threadsCount = 4  // スレッド数
    }
}
```

#### リクエストのキャッシング

```kotlin
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

class CachedUserServiceClient(private val client: UserServiceClient) {
    private val cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .recordStats()
        .build<String, User>()
    
    suspend fun getUser(userId: String): User {
        return cache.getIfPresent(userId) ?: run {
            val user = client.getUser(userId)
            cache.put(userId, user)
            user
        }
    }
    
    fun getCacheStats() = cache.stats()
}
```

---

### まとめ

この章で学んだこと:

1. ✅ **Ktor HTTPクライアントのセットアップ**
   - エンジンの選択
   - プラグインの設定
   - 依存性注入

2. ✅ **HTTPリクエストの実行**
   - GET, POST, PUT, PATCH, DELETE
   - クエリパラメータ
   - ヘッダー設定

3. ✅ **並列処理**
   - async/awaitでの並列実行
   - 構造化並行性
   - 部分的な失敗への対応

4. ✅ **エラーハンドリング**
   - HTTPステータスコードの判定
   - カスタム例外
   - グローバルエラーハンドリング

5. ✅ **レスポンス変換**
   - バックエンドDTOからモバイルDTOへの変換
   - データサイズの削減

6. ✅ **実践例**
   - ホーム画面APIの実装
   - 認証トークンの伝播

---

### 次のステップ

次は**Part 6: レジリエンスと可観測性**で、サーキットブレーカー、リトライ、ログ、メトリクスを学びます。

---

### 学習チェックリスト

- [ ] HTTPクライアントを設定できる
- [ ] GET/POST/PUT/DELETE リクエストを実行できる
- [ ] 複数のAPIを並列で呼び出せる
- [ ] エラーハンドリングを実装できる
- [ ] レスポンス変換を実装できる
- [ ] タイムアウトを設定できる
- [ ] 認証トークンを伝播できる
- [ ] BFFの集約APIを実装できる

全てチェックできたら、次のPartに進みましょう！
