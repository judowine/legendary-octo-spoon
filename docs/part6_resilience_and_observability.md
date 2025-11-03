# Part 6: レジリエンスと可観測性

## Chapter 6: Ktorでのレジリエンス実装

### 6.1 なぜレジリエンスが必要か

#### 分散システムの現実

モバイルBFFは複数のバックエンドサービスに依存します。これらのサービスは**必ず失敗する**という前提で設計する必要があります。

**よくある障害のパターン**:
```
User Service: 99.9% uptime
Order Service: 99.5% uptime
Recommendation Service: 98% uptime

全て正常に動作する確率:
0.999 × 0.995 × 0.98 = 0.974 (97.4%)

→ 2.6%の確率でいずれかが失敗している
```

**障害の種類**:
- **ネットワーク障害**: 接続できない、パケットロス
- **タイムアウト**: レスポンスが遅い、応答なし
- **サービス過負荷**: 503 Service Unavailable
- **一時的なエラー**: データベース接続失敗、キャッシュミス
- **レート制限**: 429 Too Many Requests
- **部分的な障害**: 一部の機能だけ動かない

**レジリエンスパターンの目的**:
- ✅ **障害の影響を最小化**: 一部の失敗が全体に波及しない
- ✅ **自動復旧**: 一時的な障害から自動的に回復
- ✅ **ユーザー体験の維持**: 部分的なデータでもサービスを継続
- ✅ **システムの保護**: 障害が連鎖しないようにする

---

### 6.2 Resilience4j入門

#### Resilience4jとは

**Resilience4j**: 軽量で関数型のレジリエンスライブラリ

**提供される機能**:
- **CircuitBreaker**: サーキットブレーカー
- **Retry**: リトライ
- **RateLimiter**: レート制限
- **TimeLimiter**: タイムアウト
- **Bulkhead**: バルクヘッド（並行処理制限）
- **Cache**: キャッシング

#### 依存関係の追加

```kotlin
// build.gradle.kts
dependencies {
    // Resilience4j
    implementation("io.github.resilience4j:resilience4j-kotlin:2.1.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.1.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.1.0")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.1.0")
    implementation("io.github.resilience4j:resilience4j-timelimiter:2.1.0")
    
    // Coroutinesサポート
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

---

### 6.3 CircuitBreaker（サーキットブレーカー）

#### サーキットブレーカーとは

**問題**: バックエンドサービスが障害中でも、BFFは何度もリクエストを送り続ける
→ リソースを無駄にし、復旧を遅らせる

**解決策**: サーキットブレーカーが障害を検知したら、一定時間リクエストを遮断し、サービスに回復の時間を与える

#### サーキットブレーカーの状態遷移

```
[CLOSED] 正常状態
   ↓ 失敗率が閾値を超える
[OPEN] 遮断状態（全てのリクエストを即座に失敗させる）
   ↓ 待機時間経過
[HALF_OPEN] 半開状態（テストリクエストを許可）
   ↓ 成功 → CLOSED / 失敗 → OPEN
```

**状態の説明**:

| 状態 | 動作 | 目的 |
|-----|------|------|
| **CLOSED** | 通常通りリクエストを送信 | 正常時の動作 |
| **OPEN** | リクエストを即座に失敗させる | バックエンドを保護、高速な失敗 |
| **HALF_OPEN** | 少数のリクエストで状態を確認 | 回復確認 |

#### CircuitBreakerの設定

```kotlin
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import java.time.Duration

val circuitBreakerConfig = CircuitBreakerConfig.custom()
    // 失敗率が50%を超えたらOPENに遷移
    .failureRateThreshold(50f)
    
    // スライディングウィンドウサイズ（直近10リクエストを評価）
    .slidingWindowSize(10)
    
    // 最小リクエスト数（この数に達するまでは評価しない）
    .minimumNumberOfCalls(5)
    
    // OPEN状態での待機時間（30秒後にHALF_OPENに遷移）
    .waitDurationInOpenState(Duration.ofSeconds(30))
    
    // HALF_OPEN状態で許可するリクエスト数
    .permittedNumberOfCallsInHalfOpenState(5)
    
    // スローコールの閾値（2秒以上かかるリクエストを失敗とみなす）
    .slowCallDurationThreshold(Duration.ofSeconds(2))
    
    // スローコールの割合が50%を超えたらOPENに遷移
    .slowCallRateThreshold(50f)
    
    // 記録する例外（これらの例外を失敗としてカウント）
    .recordExceptions(
        java.io.IOException::class.java,
        java.util.concurrent.TimeoutException::class.java,
        BackendException::class.java
    )
    
    // 無視する例外（これらの例外は失敗としてカウントしない）
    .ignoreExceptions(
        NotFoundException::class.java,
        BadRequestException::class.java
    )
    
    .build()

val userServiceCircuitBreaker = CircuitBreaker.of("userService", circuitBreakerConfig)
```

#### CircuitBreakerの使用

```kotlin
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

suspend fun getUserWithCircuitBreaker(
    client: HttpClient,
    circuitBreaker: CircuitBreaker,
    userId: String
): User {
    return circuitBreaker.executeSuspendFunction {
        client.get("https://user-service.com/users/$userId") {
            header("Authorization", "Bearer $accessToken")
        }.body<User>()
    }
}

// 使用例
try {
    val user = getUserWithCircuitBreaker(client, userServiceCircuitBreaker, userId)
    call.respond(user)
} catch (e: Exception) {
    when (e) {
        is io.github.resilience4j.circuitbreaker.CallNotPermittedException -> {
            // サーキットブレーカーがOPEN状態
            logger.warn("User service circuit breaker is OPEN")
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("SERVICE_UNAVAILABLE", "User service temporarily unavailable")
            )
        }
        else -> {
            logger.error("Failed to get user", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "Failed to get user")
            )
        }
    }
}
```

#### CircuitBreakerのイベント監視

```kotlin
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.core.registry.EntryAddedEvent
import io.github.resilience4j.core.registry.EntryRemovedEvent
import io.github.resilience4j.core.registry.EntryReplacedEvent

// イベントリスナーを登録
userServiceCircuitBreaker.eventPublisher
    .onSuccess { event ->
        logger.info("User service call succeeded: ${event.elapsedDuration}")
    }
    .onError { event ->
        logger.warn("User service call failed: ${event.throwable.message}")
    }
    .onStateTransition { event ->
        logger.warn(
            "User service circuit breaker state changed: ${event.stateTransition.fromState} -> ${event.stateTransition.toState}"
        )
    }
    .onSlowCallRateExceeded { event ->
        logger.warn("User service slow call rate exceeded: ${event.slowCallRate}%")
    }
    .onFailureRateExceeded { event ->
        logger.error("User service failure rate exceeded: ${event.failureRate}%")
    }
```

#### フォールバック処理

```kotlin
suspend fun getUserWithFallback(
    client: HttpClient,
    circuitBreaker: CircuitBreaker,
    userId: String
): User? {
    return try {
        circuitBreaker.executeSuspendFunction {
            client.get("https://user-service.com/users/$userId") {
                header("Authorization", "Bearer $accessToken")
            }.body<User>()
        }
    } catch (e: Exception) {
        logger.warn("Failed to get user, returning cached data", e)
        
        // キャッシュから取得
        cache.getIfPresent(userId) ?: run {
            // キャッシュもない場合はnullを返す
            logger.error("No cached data available for user $userId")
            null
        }
    }
}
```

---

### 6.4 Retry（リトライ）

#### リトライパターン

**一時的な障害**は、少し待ってから再試行すると成功することが多いです。

**リトライすべき障害**:
- ✅ ネットワークタイムアウト
- ✅ 503 Service Unavailable
- ✅ 502 Bad Gateway
- ✅ 504 Gateway Timeout
- ✅ 接続失敗

**リトライすべきでない障害**:
- ❌ 400 Bad Request（リクエストが不正）
- ❌ 401 Unauthorized（認証エラー）
- ❌ 403 Forbidden（権限エラー）
- ❌ 404 Not Found（リソースが存在しない）
- ❌ 422 Unprocessable Entity（バリデーションエラー）

#### Retryの設定

```kotlin
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import java.time.Duration

val retryConfig = RetryConfig.custom<Any>()
    // 最大リトライ回数
    .maxAttempts(3)
    
    // 待機時間（指数バックオフ）
    .waitDuration(Duration.ofMillis(1000))  // 初回: 1秒
    
    // 指数バックオフ（倍率）
    .intervalFunction { attemptNumber ->
        // 1秒 → 2秒 → 4秒
        Duration.ofMillis(1000L * (1L shl (attemptNumber - 1)))
    }
    
    // ジッター（ランダム性を追加して、リトライ嵐を防ぐ）
    .intervalFunction(
        io.github.resilience4j.core.IntervalFunction
            .ofExponentialRandomBackoff(1000, 2.0, 0.5)
    )
    
    // リトライする例外
    .retryExceptions(
        java.io.IOException::class.java,
        java.util.concurrent.TimeoutException::class.java
    )
    
    // リトライしない例外
    .ignoreExceptions(
        BadRequestException::class.java,
        UnauthorizedException::class.java,
        ForbiddenException::class.java,
        NotFoundException::class.java
    )
    
    // カスタム条件（HTTPステータスコードで判定）
    .retryOnResult { response ->
        // 5xxエラーの場合のみリトライ
        response is HttpResponse && response.status.value >= 500
    }
    
    .build()

val userServiceRetry = Retry.of("userService", retryConfig)
```

#### Retryの使用

```kotlin
suspend fun getUserWithRetry(
    client: HttpClient,
    retry: Retry,
    userId: String
): User {
    return retry.executeSuspendFunction {
        client.get("https://user-service.com/users/$userId") {
            header("Authorization", "Bearer $accessToken")
        }.body<User>()
    }
}
```

#### リトライイベントの監視

```kotlin
userServiceRetry.eventPublisher
    .onRetry { event ->
        logger.warn(
            "Retrying user service call (attempt ${event.numberOfRetryAttempts}): ${event.lastThrowable.message}"
        )
    }
    .onSuccess { event ->
        if (event.numberOfRetryAttempts > 0) {
            logger.info("User service call succeeded after ${event.numberOfRetryAttempts} retries")
        }
    }
    .onError { event ->
        logger.error(
            "User service call failed after ${event.numberOfRetryAttempts} retries",
            event.lastThrowable
        )
    }
```

#### CircuitBreakerとRetryの組み合わせ

```kotlin
suspend fun getUserWithResiliency(
    client: HttpClient,
    circuitBreaker: CircuitBreaker,
    retry: Retry,
    userId: String
): User {
    // 順序: Retry → CircuitBreaker
    // リトライしてもダメな場合にCircuitBreakerがOPENになる
    return retry.executeSuspendFunction {
        circuitBreaker.executeSuspendFunction {
            client.get("https://user-service.com/users/$userId") {
                header("Authorization", "Bearer $accessToken")
            }.body<User>()
        }
    }
}
```

---

### 6.5 RateLimiter（レート制限）

#### なぜレート制限が必要か

**問題**:
- バックエンドサービスが過負荷になる
- DDoS攻撃からの保護
- APIの公平な利用

**Ktorのレート制限**:
- BFFへのリクエストを制限
- バックエンドへのリクエストを制限

#### Ktor RateLimitプラグイン

```kotlin
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimit() {
    install(RateLimit) {
        // グローバルレート制限
        global {
            rateLimiter(limit = 100, refillPeriod = 60.seconds)
        }
        
        // 認証済みユーザー用
        register(RateLimitName("authenticated")) {
            rateLimiter(limit = 1000, refillPeriod = 3600.seconds)
            requestKey { call ->
                call.principal<JWTPrincipal>()
                    ?.payload
                    ?.getClaim("user_id")
                    ?.asString()
                    ?: call.request.origin.remoteAddress
            }
        }
        
        // API別のレート制限
        register(RateLimitName("search-api")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call ->
                call.principal<JWTPrincipal>()
                    ?.payload
                    ?.getClaim("user_id")
                    ?.asString()
            }
        }
    }
}
```

#### レート制限の適用

```kotlin
routing {
    // グローバルレート制限が適用される
    get("/api/v1/public/products") {
        val products = productService.getProducts()
        call.respond(products)
    }
    
    // 認証済みユーザー用のレート制限
    rateLimit(RateLimitName("authenticated")) {
        authenticate("auth-jwt") {
            get("/api/v1/profile") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("user_id").asString()
                val user = userService.getUser(userId)
                call.respond(user)
            }
        }
    }
    
    // 検索APIは厳しいレート制限
    rateLimit(RateLimitName("search-api")) {
        authenticate("auth-jwt") {
            get("/api/v1/search") {
                val query = call.request.queryParameters["q"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val results = searchService.search(query)
                call.respond(results)
            }
        }
    }
}
```

#### レート制限超過時のレスポンス

レート制限を超えると、以下のヘッダーが自動的に追加されます：

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1699000000
Retry-After: 60
```

#### カスタムレート制限レスポンス

```kotlin
install(RateLimit) {
    global {
        rateLimiter(limit = 100, refillPeriod = 60.seconds)
    }
}

install(StatusPages) {
    exception<RateLimitExceededException> { call, cause ->
        call.response.header("Retry-After", "60")
        call.respond(
            HttpStatusCode.TooManyRequests,
            ErrorResponse(
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please try again later."
            )
        )
    }
}
```

---

### 6.6 キャッシング（Caffeine + Redis）

#### キャッシング戦略

**L1キャッシュ（Caffeine）**: インメモリ、超高速
**L2キャッシュ（Redis）**: 分散キャッシュ、複数インスタンス間で共有

```
リクエスト
  ↓
L1キャッシュ（Caffeine）
  ↓ キャッシュミス
L2キャッシュ（Redis）
  ↓ キャッシュミス
バックエンドAPI
```

#### Caffeineのセットアップ

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
}
```

```kotlin
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

class CacheConfig {
    // ユーザー情報キャッシュ（短命）
    val userCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .recordStats()
        .build<String, User>()
    
    // 商品情報キャッシュ（長命）
    val productCache = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .recordStats()
        .build<String, Product>()
    
    // 検索結果キャッシュ（短命）
    val searchCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .recordStats()
        .build<String, SearchResult>()
}
```

#### キャッシュの使用

```kotlin
class UserService(
    private val userClient: UserServiceClient,
    private val cache: Cache<String, User>
) {
    suspend fun getUser(userId: String): User {
        // キャッシュから取得を試みる
        return cache.getIfPresent(userId) ?: run {
            // キャッシュミス: バックエンドから取得
            val user = userClient.getUser(userId)
            
            // キャッシュに保存
            cache.put(userId, user)
            
            user
        }
    }
    
    suspend fun updateUser(userId: String, request: UpdateUserRequest): User {
        val user = userClient.updateUser(userId, request)
        
        // キャッシュを更新
        cache.put(userId, user)
        
        return user
    }
    
    fun invalidateUser(userId: String) {
        cache.invalidate(userId)
    }
    
    fun getCacheStats() = cache.stats()
}
```

#### Redisのセットアップ

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
}
```

```kotlin
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class RedisCache(redisUrl: String) {
    private val client: RedisClient = RedisClient.create(redisUrl)
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val commands: RedisCoroutinesCommands<String, String> = connection.coroutines()
    
    suspend fun <T> get(key: String, clazz: Class<T>): T? {
        val value = commands.get(key) ?: return null
        return Json.decodeFromString(value)
    }
    
    suspend fun <T> set(key: String, value: T, ttlSeconds: Long) {
        val json = Json.encodeToString(value)
        commands.setex(key, ttlSeconds, json)
    }
    
    suspend fun delete(key: String) {
        commands.del(key)
    }
    
    fun close() {
        connection.close()
        client.shutdown()
    }
}
```

#### 2層キャッシュの実装

```kotlin
class TwoLevelCacheUserService(
    private val userClient: UserServiceClient,
    private val l1Cache: Cache<String, User>,
    private val l2Cache: RedisCache
) {
    suspend fun getUser(userId: String): User {
        // L1キャッシュチェック
        l1Cache.getIfPresent(userId)?.let { return it }
        
        // L2キャッシュチェック
        l2Cache.get("user:$userId", User::class.java)?.let { user ->
            // L1に格納
            l1Cache.put(userId, user)
            return user
        }
        
        // バックエンドから取得
        val user = userClient.getUser(userId)
        
        // L1とL2に格納
        l1Cache.put(userId, user)
        l2Cache.set("user:$userId", user, 300)  // 5分
        
        return user
    }
}
```

#### Cache-Asideパターン

```kotlin
suspend fun <T> cacheAside(
    key: String,
    ttl: Long,
    l1Cache: Cache<String, T>,
    l2Cache: RedisCache,
    loader: suspend () -> T
): T {
    // L1チェック
    l1Cache.getIfPresent(key)?.let { return it }
    
    // L2チェック
    l2Cache.get(key, T::class.java)?.let { value ->
        l1Cache.put(key, value)
        return value
    }
    
    // データソースから取得
    val value = loader()
    
    // キャッシュに格納
    l1Cache.put(key, value)
    l2Cache.set(key, value, ttl)
    
    return value
}

// 使用例
val user = cacheAside(
    key = "user:$userId",
    ttl = 300,
    l1Cache = userCache,
    l2Cache = redisCache
) {
    userClient.getUser(userId)
}
```

---

### 6.7 ログとモニタリング（Ktor特化）

#### Logbackの設定

```xml
<!-- src/main/resources/logback.xml -->
<configuration>
    <!-- コンソール出力（開発環境） -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- JSON形式（本番環境） -->
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>method</includeMdcKeyName>
            <includeMdcKeyName>path</includeMdcKeyName>
        </encoder>
    </appender>
    
    <!-- ファイル出力 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application-%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
    
    <logger name="io.ktor" level="INFO" />
    <logger name="io.netty" level="WARN" />
</configuration>
```

#### CallLoggingプラグイン

```kotlin
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO
        
        // ログ対象のパスをフィルタ
        filter { call ->
            call.request.path().startsWith("/api")
        }
        
        // ログフォーマット
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val duration = call.processingTimeMillis()
            
            "$method $path - $status (${duration}ms)"
        }
        
        // MDC（Mapped Diagnostic Context）
        mdc("requestId") {
            it.request.header("X-Request-ID") ?: UUID.randomUUID().toString()
        }
        
        mdc("userId") { call ->
            call.principal<JWTPrincipal>()
                ?.payload
                ?.getClaim("user_id")
                ?.asString()
        }
        
        mdc("method") {
            it.request.httpMethod.value
        }
        
        mdc("path") {
            it.request.path()
        }
    }
}
```

#### CallIdプラグイン

```kotlin
import io.ktor.server.plugins.callid.*
import java.util.UUID

fun Application.configureCallId() {
    install(CallId) {
        // リクエストヘッダーからIDを取得
        header("X-Request-ID")
        
        // ヘッダーがない場合はUUIDを生成
        generate { UUID.randomUUID().toString() }
        
        // 検証
        verify { callId ->
            callId.isNotEmpty()
        }
        
        // レスポンスヘッダーに追加
        replyToHeader("X-Request-ID")
    }
}
```

#### 構造化ログ

```kotlin
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class UserService {
    private val logger = LoggerFactory.getLogger(UserService::class.java)
    
    suspend fun getUser(userId: String): User {
        // MDCにユーザーIDを追加
        MDC.put("userId", userId)
        
        try {
            logger.info("Fetching user data")
            val user = userClient.getUser(userId)
            logger.info("User data fetched successfully")
            return user
        } catch (e: Exception) {
            logger.error("Failed to fetch user data", e)
            throw e
        } finally {
            // MDCをクリーンアップ
            MDC.remove("userId")
        }
    }
}
```

#### ログレベルの使い分け

| レベル | 用途 | 例 |
|-------|------|-----|
| **TRACE** | 詳細なデバッグ情報 | 変数の値、処理ステップ |
| **DEBUG** | デバッグ情報 | SQLクエリ、APIリクエスト |
| **INFO** | 重要なイベント | リクエスト開始/完了、ユーザーログイン |
| **WARN** | 警告（異常だが処理継続） | キャッシュミス、リトライ |
| **ERROR** | エラー（処理失敗） | API呼び出し失敗、例外 |

```kotlin
logger.trace("Processing request: $requestDetails")
logger.debug("SQL query: $sql")
logger.info("User logged in: $userId")
logger.warn("Cache miss for key: $key, fetching from backend")
logger.error("Failed to save order", exception)
```

---

### 6.8 メトリクス（Micrometer）

#### Micrometerのセットアップ

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.ktor:ktor-server-metrics-micrometer")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
}
```

```kotlin
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

fun Application.configureMetrics() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        
        // メトリクス名のプレフィックス
        meterBinders = emptyList()
    }
    
    // Prometheusエンドポイント
    routing {
        get("/metrics") {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}
```

#### カスタムメトリクス

```kotlin
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer

class UserService(
    private val userClient: UserServiceClient,
    private val meterRegistry: MeterRegistry
) {
    // カウンター
    private val userFetchCounter = Counter.builder("user.fetch.count")
        .description("Number of user fetch operations")
        .tag("service", "user")
        .register(meterRegistry)
    
    // タイマー
    private val userFetchTimer = Timer.builder("user.fetch.duration")
        .description("Duration of user fetch operations")
        .tag("service", "user")
        .register(meterRegistry)
    
    suspend fun getUser(userId: String): User {
        userFetchCounter.increment()
        
        return userFetchTimer.recordSuspend {
            userClient.getUser(userId)
        }
    }
}

// Timer拡張関数
suspend fun <T> Timer.recordSuspend(block: suspend () -> T): T {
    val start = System.nanoTime()
    try {
        return block()
    } finally {
        val duration = System.nanoTime() - start
        record(duration, java.util.concurrent.TimeUnit.NANOSECONDS)
    }
}
```

#### 主要なメトリクス

**リクエストメトリクス**:
```kotlin
// リクエスト数
http_server_requests_total{method="GET",path="/api/v1/users",status="200"} 1500

// レイテンシー
http_server_requests_duration_seconds{method="GET",path="/api/v1/users",quantile="0.95"} 0.150

// エラー率
http_server_requests_errors_total{method="GET",path="/api/v1/users",error_type="timeout"} 5
```

**サーキットブレーカーメトリクス**:
```kotlin
// 状態
circuitbreaker_state{name="userService",state="closed"} 1

// 呼び出し数
circuitbreaker_calls_total{name="userService",kind="successful"} 1000
circuitbreaker_calls_total{name="userService",kind="failed"} 50

// 失敗率
circuitbreaker_failure_rate{name="userService"} 0.048
```

**キャッシュメトリクス**:
```kotlin
// ヒット率
cache_hit_ratio{cache="user"} 0.85

// エントリ数
cache_size{cache="user"} 850

// エビクション数
cache_evictions_total{cache="user"} 150
```

---

### 6.9 CloudWatch統合

#### CloudWatch Logsへの出力

```kotlin
// build.gradle.kts
dependencies {
    implementation("software.amazon.awssdk:cloudwatchlogs:2.20.0")
}
```

**Logbackでの設定**:
```xml
<appender name="CLOUDWATCH" class="ca.pjer.logback.AwsLogsAppender">
    <layout>
        <pattern>%d{yyyyMMdd'T'HHmmss} %thread %level %logger{15} %msg%n</pattern>
    </layout>
    <logGroupName>/ecs/ktor-bff</logGroupName>
    <logStreamName>${HOSTNAME}</logStreamName>
    <logRegion>ap-northeast-1</logRegion>
</appender>
```

#### CloudWatch Metricsへの送信

```kotlin
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.*
import java.time.Instant

class CloudWatchMetrics(
    private val namespace: String,
    private val client: CloudWatchClient
) {
    suspend fun publishMetric(
        metricName: String,
        value: Double,
        unit: StandardUnit = StandardUnit.COUNT,
        dimensions: Map<String, String> = emptyMap()
    ) {
        val metricDatum = MetricDatum.builder()
            .metricName(metricName)
            .value(value)
            .unit(unit)
            .timestamp(Instant.now())
            .dimensions(
                dimensions.map { (key, value) ->
                    Dimension.builder().name(key).value(value).build()
                }
            )
            .build()
        
        val request = PutMetricDataRequest.builder()
            .namespace(namespace)
            .metricData(metricDatum)
            .build()
        
        client.putMetricData(request)
    }
}

// 使用例
val cloudWatch = CloudWatchMetrics("KtorBFF", CloudWatchClient.create())

cloudWatch.publishMetric(
    metricName = "UserFetchDuration",
    value = 150.0,
    unit = StandardUnit.MILLISECONDS,
    dimensions = mapOf(
        "Service" to "UserService",
        "Environment" to "production"
    )
)
```

---

### 6.10 ヘルスチェック

#### ヘルスチェックエンドポイント

```kotlin
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String,
    val checks: Map<String, HealthCheck>
)

@Serializable
data class HealthCheck(
    val status: String,
    val message: String? = null,
    val duration: Long? = null
)

fun Route.healthRoutes(
    databaseHealthCheck: suspend () -> HealthCheck,
    redisHealthCheck: suspend () -> HealthCheck,
    backendServicesHealthCheck: suspend () -> Map<String, HealthCheck>
) {
    // シンプルなヘルスチェック（ALB用）
    get("/health") {
        call.respondText("OK")
    }
    
    // 詳細なヘルスチェック
    get("/health/detailed") {
        val checks = mutableMapOf<String, HealthCheck>()
        
        // データベース
        checks["database"] = try {
            databaseHealthCheck()
        } catch (e: Exception) {
            HealthCheck("unhealthy", e.message)
        }
        
        // Redis
        checks["redis"] = try {
            redisHealthCheck()
        } catch (e: Exception) {
            HealthCheck("unhealthy", e.message)
        }
        
        // バックエンドサービス
        checks.putAll(backendServicesHealthCheck())
        
        // 全体のステータス
        val overallStatus = if (checks.values.all { it.status == "healthy" }) {
            "healthy"
        } else {
            "unhealthy"
        }
        
        val response = HealthResponse(
            status = overallStatus,
            timestamp = Instant.now().toString(),
            checks = checks
        )
        
        val statusCode = if (overallStatus == "healthy") {
            HttpStatusCode.OK
        } else {
            HttpStatusCode.ServiceUnavailable
        }
        
        call.respond(statusCode, response)
    }
}
```

#### ヘルスチェックの実装

```kotlin
class DatabaseHealthCheck(private val database: Database) {
    suspend fun check(): HealthCheck {
        val start = System.currentTimeMillis()
        
        return try {
            transaction(database) {
                exec("SELECT 1")
            }
            
            val duration = System.currentTimeMillis() - start
            HealthCheck("healthy", "Database connection successful", duration)
        } catch (e: Exception) {
            HealthCheck("unhealthy", "Database connection failed: ${e.message}")
        }
    }
}

class RedisHealthCheck(private val redis: RedisCache) {
    suspend fun check(): HealthCheck {
        val start = System.currentTimeMillis()
        
        return try {
            redis.set("health_check", "ping", 10)
            redis.get("health_check", String::class.java)
            redis.delete("health_check")
            
            val duration = System.currentTimeMillis() - start
            HealthCheck("healthy", "Redis connection successful", duration)
        } catch (e: Exception) {
            HealthCheck("unhealthy", "Redis connection failed: ${e.message}")
        }
    }
}

class BackendServicesHealthCheck(
    private val userClient: HttpClient,
    private val orderClient: HttpClient
) {
    suspend fun check(): Map<String, HealthCheck> {
        val checks = mutableMapOf<String, HealthCheck>()
        
        // User Service
        checks["userService"] = coroutineScope {
            async {
                try {
                    val start = System.currentTimeMillis()
                    userClient.get("https://user-service.com/health")
                    val duration = System.currentTimeMillis() - start
                    HealthCheck("healthy", null, duration)
                } catch (e: Exception) {
                    HealthCheck("unhealthy", e.message)
                }
            }.await()
        }
        
        // Order Service
        checks["orderService"] = coroutineScope {
            async {
                try {
                    val start = System.currentTimeMillis()
                    orderClient.get("https://order-service.com/health")
                    val duration = System.currentTimeMillis() - start
                    HealthCheck("healthy", null, duration)
                } catch (e: Exception) {
                    HealthCheck("unhealthy", e.message)
                }
            }.await()
        }
        
        return checks
    }
}
```

---

### まとめ

この章で学んだこと:

1. ✅ **レジリエンスパターン**
   - CircuitBreaker: 障害の連鎖を防ぐ
   - Retry: 一時的な障害から回復
   - RateLimit: 過負荷を防ぐ

2. ✅ **キャッシング**
   - Caffeine: L1キャッシュ
   - Redis: L2キャッシュ
   - 2層キャッシュ戦略

3. ✅ **ログ**
   - CallLogging: リクエストログ
   - CallId: リクエストID
   - 構造化ログ（JSON）

4. ✅ **メトリクス**
   - Micrometer: カスタムメトリクス
   - Prometheus形式
   - CloudWatch統合

5. ✅ **ヘルスチェック**
   - シンプルなヘルスチェック
   - 詳細なヘルスチェック
   - 依存サービスの監視

---

### 次のステップ

次は**Part 7: テストとCI/CD**で、ユニットテスト、統合テスト、GitHub Actionsを学びます。

---

### 学習チェックリスト

- [ ] CircuitBreakerを設定・使用できる
- [ ] Retryを実装できる
- [ ] RateLimitを設定できる
- [ ] Caffeineでキャッシングを実装できる
- [ ] CallLoggingを設定できる
- [ ] カスタムメトリクスを作成できる
- [ ] ヘルスチェックエンドポイントを実装できる
- [ ] CloudWatch Logsに出力できる

全てチェックできたら、次のPartに進みましょう！
