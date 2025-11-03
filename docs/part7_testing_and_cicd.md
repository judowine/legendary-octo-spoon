# Part 7: テストとCI/CD

## Chapter 7: Ktorアプリケーションのテスト戦略

### 7.1 テストピラミッド

#### テスト戦略の全体像

```
       /\
      /  \  E2E Tests (少ない)
     /____\
    /      \
   / Integration \ (中程度)
  /__Tests_____\
 /              \
/  Unit Tests    \ (多い)
/________________\
```

**テストの種類と割合**:
- **ユニットテスト**: 70% - 高速、独立、詳細
- **統合テスト**: 20% - 中速、依存関係あり
- **E2Eテスト**: 10% - 低速、全体の動作確認

#### Ktorアプリケーションのテスト層

| 層 | テスト対象 | ツール | 実行時間 |
|---|-----------|--------|---------|
| **ユニット** | 関数、クラス、ロジック | Kotest, MockK | ミリ秒 |
| **統合** | API、DB、外部サービス | Ktor TestEngine, TestContainers | 秒 |
| **E2E** | 全体フロー | 実際のHTTPクライアント | 分 |

---

### 7.2 テストツールのセットアップ

#### 依存関係の追加

```kotlin
// build.gradle.kts
dependencies {
    // Ktor Test
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    
    // Kotest
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    
    // MockK
    testImplementation("io.mockk:mockk:1.13.8")
    
    // TestContainers
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

---

### 7.3 ユニットテスト（Kotest + MockK）

#### Kotestの基本

**FunSpec**: 関数スタイルのテスト

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class UserServiceTest : FunSpec({
    test("ユーザー名を結合できる") {
        val user = User("123", "John", "Doe")
        user.fullName() shouldBe "John Doe"
    }
    
    test("空のユーザー名は空文字列を返す") {
        val user = User("123", "", "")
        user.fullName() shouldBe ""
    }
})
```

**BehaviorSpec**: BDD（振る舞い駆動開発）スタイル

```kotlin
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class OrderServiceTest : BehaviorSpec({
    given("有効な注文リクエスト") {
        val request = CreateOrderRequest(
            userId = "user123",
            productId = "prod456",
            quantity = 2
        )
        
        `when`("注文を作成する") {
            val order = orderService.createOrder(request)
            
            then("注文IDが生成される") {
                order.id shouldNotBe null
            }
            
            then("ステータスはpendingになる") {
                order.status shouldBe "pending"
            }
            
            then("合計金額が計算される") {
                order.total shouldBe 79.98
            }
        }
    }
    
    given("在庫がない商品") {
        val request = CreateOrderRequest(
            userId = "user123",
            productId = "prod999",
            quantity = 1
        )
        
        `when`("注文を作成しようとする") {
            val exception = shouldThrow<OutOfStockException> {
                orderService.createOrder(request)
            }
            
            then("OutOfStockException が投げられる") {
                exception.message shouldBe "Product out of stock"
            }
        }
    }
})
```

#### MockKでのモック

**基本的なモック**:

```kotlin
import io.mockk.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

class UserServiceTest : FunSpec({
    test("ユーザーを取得できる") = runBlocking {
        // Arrange: モックを作成
        val mockRepository = mockk<UserRepository>()
        val userService = UserService(mockRepository)
        
        val expectedUser = User("123", "John", "Doe", "john@example.com")
        
        // モックの振る舞いを定義
        coEvery { mockRepository.findById("123") } returns expectedUser
        
        // Act: テスト対象のメソッドを実行
        val result = userService.getUser("123")
        
        // Assert: 結果を検証
        result shouldBe expectedUser
        
        // モックが呼ばれたことを検証
        coVerify(exactly = 1) { mockRepository.findById("123") }
    }
    
    test("存在しないユーザーはNotFoundException を投げる") = runBlocking {
        val mockRepository = mockk<UserRepository>()
        val userService = UserService(mockRepository)
        
        coEvery { mockRepository.findById("999") } returns null
        
        shouldThrow<NotFoundException> {
            userService.getUser("999")
        }
        
        coVerify { mockRepository.findById("999") }
    }
})
```

**複数のメソッド呼び出しのモック**:

```kotlin
test("ホームデータを集約できる") = runBlocking {
    // モックの作成
    val mockUserClient = mockk<UserServiceClient>()
    val mockOrderClient = mockk<OrderServiceClient>()
    val mockRecClient = mockk<RecommendationServiceClient>()
    
    val homeService = HomeService(mockUserClient, mockOrderClient, mockRecClient)
    
    // モックの振る舞いを定義
    coEvery { mockUserClient.getUser("user123") } returns User(
        id = "user123",
        name = "John Doe",
        email = "john@example.com"
    )
    
    coEvery { mockOrderClient.getOrders("user123", 5) } returns listOf(
        Order("order1", "user123", "Product A", 2, "delivered"),
        Order("order2", "user123", "Product B", 1, "shipped")
    )
    
    coEvery { mockRecClient.getRecommendations("user123", 10) } returns listOf(
        Product("prod1", "Recommendation 1", 29.99, "https://example.com/1.jpg"),
        Product("prod2", "Recommendation 2", 39.99, "https://example.com/2.jpg")
    )
    
    // テスト実行
    val result = homeService.getHomeData("user123")
    
    // 検証
    result.user.name shouldBe "John Doe"
    result.recentOrders.size shouldBe 2
    result.recommendations.size shouldBe 2
    
    // 全てのクライアントが呼ばれたことを確認
    coVerify { mockUserClient.getUser("user123") }
    coVerify { mockOrderClient.getOrders("user123", 5) }
    coVerify { mockRecClient.getRecommendations("user123", 10) }
}
```

**例外のモック**:

```kotlin
test("バックエンドエラー時はBackendException を投げる") = runBlocking {
    val mockUserClient = mockk<UserServiceClient>()
    val userService = UserService(mockUserClient)
    
    // 例外を投げるようにモック
    coEvery { mockUserClient.getUser("123") } throws IOException("Connection refused")
    
    shouldThrow<BackendException> {
        userService.getUser("123")
    }
    
    coVerify { mockUserClient.getUser("123") }
}
```

**relaxedモック**:

```kotlin
test("relaxedモックは未定義のメソッドにデフォルト値を返す") = runBlocking {
    // relaxed = true でデフォルト値を返すモック
    val mockRepository = mockk<UserRepository>(relaxed = true)
    
    // メソッドを定義しなくても null が返る
    val result = mockRepository.findById("123")
    result shouldBe null
}
```

#### Kotestのマッチャー

```kotlin
import io.kotest.matchers.*
import io.kotest.matchers.collections.*
import io.kotest.matchers.string.*

test("様々なマッチャー") {
    // 等価性
    "hello" shouldBe "hello"
    "hello" shouldNotBe "world"
    
    // null チェック
    val user: User? = getUser()
    user shouldNotBe null
    user!!.name shouldBe "John"
    
    // コレクション
    listOf(1, 2, 3) shouldContain 2
    listOf(1, 2, 3) shouldHaveSize 3
    listOf(1, 2, 3) shouldContainAll listOf(1, 3)
    
    // 文字列
    "Hello World" shouldContain "World"
    "hello@example.com" shouldMatch ".*@example\\.com".toRegex()
    
    // 数値
    10 shouldBeGreaterThan 5
    10 shouldBeLessThan 20
    10 shouldBeInRange 5..15
    
    // 例外
    shouldThrow<IllegalArgumentException> {
        validateInput("")
    }
    
    // 型チェック
    val obj: Any = "string"
    obj shouldBeInstanceOf<String>()
}
```

---

### 7.4 統合テスト（Ktor TestEngine）

#### TestEngineの基本

```kotlin
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UserRoutesTest : FunSpec({
    test("GET /api/v1/users/{id} はユーザーを返す") = testApplication {
        // Ktorアプリケーションをセットアップ
        application {
            module()  // 本番と同じmodule関数
        }
        
        // HTTPクライアントを作成
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        
        // リクエストを送信
        val response = client.get("/api/v1/users/123")
        
        // ステータスコードを検証
        response.status shouldBe HttpStatusCode.OK
        
        // レスポンスボディを検証
        val user = response.body<User>()
        user.id shouldBe "123"
        user.name shouldBe "John Doe"
    }
    
    test("GET /api/v1/users/999 は404を返す") = testApplication {
        application {
            module()
        }
        
        val response = client.get("/api/v1/users/999")
        
        response.status shouldBe HttpStatusCode.NotFound
        
        val error = response.body<ErrorResponse>()
        error.code shouldBe "NOT_FOUND"
    }
    
    test("POST /api/v1/users はユーザーを作成する") = testApplication {
        application {
            module()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        
        val request = CreateUserRequest(
            name = "Jane Doe",
            email = "jane@example.com"
        )
        
        val response = client.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        
        response.status shouldBe HttpStatusCode.Created
        
        val user = response.body<User>()
        user.name shouldBe "Jane Doe"
        user.email shouldBe "jane@example.com"
    }
})
```

#### 認証付きエンドポイントのテスト

```kotlin
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*

test("認証が必要なエンドポイント") = testApplication {
    application {
        module()
    }
    
    val client = createClient {
        install(ContentNegotiation) {
            json()
        }
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(accessToken = "test-token", refreshToken = "")
                }
            }
        }
    }
    
    val response = client.get("/api/v1/profile")
    
    response.status shouldBe HttpStatusCode.OK
}

test("認証なしでアクセスすると401を返す") = testApplication {
    application {
        module()
    }
    
    val response = client.get("/api/v1/profile")
    
    response.status shouldBe HttpStatusCode.Unauthorized
}
```

#### カスタムヘッダーのテスト

```kotlin
test("X-Request-ID ヘッダーが追加される") = testApplication {
    application {
        module()
    }
    
    val response = client.get("/api/v1/users/123")
    
    response.headers["X-Request-ID"] shouldNotBe null
}

test("カスタムヘッダーを送信できる") = testApplication {
    application {
        module()
    }
    
    val response = client.get("/api/v1/users/123") {
        header("X-Custom-Header", "custom-value")
    }
    
    response.status shouldBe HttpStatusCode.OK
}
```

---

### 7.5 HTTPクライアントのテスト（MockEngine）

#### MockEngineの使用

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*

test("HTTPクライアントをモックする") = runBlocking {
    // MockEngineを作成
    val mockEngine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/users/123" -> {
                respond(
                    content = ByteReadChannel(
                        """{"id":"123","name":"John Doe","email":"john@example.com"}"""
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            "/users/999" -> {
                respond(
                    content = ByteReadChannel("""{"error":"Not Found"}"""),
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            else -> {
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.NotFound
                )
            }
        }
    }
    
    // モックエンジンを使ってクライアントを作成
    val client = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json()
        }
    }
    
    // UserServiceClientをテスト
    val userClient = UserServiceClient(client, "https://api.example.com")
    
    // 正常系
    val user = userClient.getUser("123")
    user.name shouldBe "John Doe"
    
    // 異常系
    shouldThrow<NotFoundException> {
        userClient.getUser("999")
    }
    
    client.close()
}
```

#### タイムアウトのテスト

```kotlin
test("タイムアウトをテストする") = runBlocking {
    val mockEngine = MockEngine { request ->
        // 遅延をシミュレート
        delay(10000)
        respond(
            content = ByteReadChannel("""{"id":"123"}"""),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    
    val client = HttpClient(mockEngine) {
        install(HttpTimeout) {
            requestTimeoutMillis = 1000  // 1秒
        }
        install(ContentNegotiation) {
            json()
        }
    }
    
    val userClient = UserServiceClient(client, "https://api.example.com")
    
    shouldThrow<TimeoutException> {
        userClient.getUser("123")
    }
    
    client.close()
}
```

#### リトライのテスト

```kotlin
test("リトライが機能する") = runBlocking {
    var callCount = 0
    
    val mockEngine = MockEngine { request ->
        callCount++
        
        if (callCount < 3) {
            // 最初の2回は失敗
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError
            )
        } else {
            // 3回目は成功
            respond(
                content = ByteReadChannel("""{"id":"123","name":"John"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    }
    
    val client = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json()
        }
    }
    
    val retry = Retry.of("test", RetryConfig.custom<Any>().maxAttempts(3).build())
    val userClient = UserServiceClientWithRetry(client, retry, "https://api.example.com")
    
    val user = userClient.getUser("123")
    
    user.name shouldBe "John"
    callCount shouldBe 3  // 3回呼ばれたことを確認
    
    client.close()
}
```

---

### 7.6 データベーステスト（TestContainers）

#### TestContainersのセットアップ

```kotlin
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

@Testcontainers
class UserRepositoryTest : FunSpec({
    // PostgreSQLコンテナを起動
    val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
        withDatabaseName("testdb")
        withUsername("testuser")
        withPassword("testpass")
    }
    
    beforeSpec {
        postgres.start()
        
        // データベース接続
        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
        
        // テーブル作成
        transaction {
            SchemaUtils.create(Users)
        }
    }
    
    afterSpec {
        transaction {
            SchemaUtils.drop(Users)
        }
        postgres.stop()
    }
    
    beforeTest {
        // 各テスト前にデータをクリア
        transaction {
            Users.deleteAll()
        }
    }
    
    test("ユーザーを作成できる") {
        val repository = UserRepositoryImpl()
        
        val user = repository.create("John Doe", "john@example.com")
        
        user.name shouldBe "John Doe"
        user.email shouldBe "john@example.com"
        user.id shouldNotBe null
    }
    
    test("IDでユーザーを取得できる") {
        val repository = UserRepositoryImpl()
        
        val created = repository.create("Jane Doe", "jane@example.com")
        val found = repository.findById(created.id)
        
        found shouldNotBe null
        found!!.name shouldBe "Jane Doe"
    }
    
    test("存在しないIDはnullを返す") {
        val repository = UserRepositoryImpl()
        
        val found = repository.findById(999)
        
        found shouldBe null
    }
    
    test("メールアドレスでユーザーを取得できる") {
        val repository = UserRepositoryImpl()
        
        repository.create("John Doe", "john@example.com")
        val found = repository.findByEmail("john@example.com")
        
        found shouldNotBe null
        found!!.name shouldBe "John Doe"
    }
    
    test("重複したメールアドレスでエラーが発生する") {
        val repository = UserRepositoryImpl()
        
        repository.create("John Doe", "john@example.com")
        
        shouldThrow<Exception> {
            repository.create("Jane Doe", "john@example.com")
        }
    }
})
```

#### トランザクションのテスト

```kotlin
test("トランザクション内で例外が発生するとロールバックされる") {
    val repository = UserRepositoryImpl()
    
    shouldThrow<Exception> {
        transaction {
            // ユーザーを作成
            repository.create("John Doe", "john@example.com")
            
            // 例外を投げる
            throw RuntimeException("Test exception")
        }
    }
    
    // ロールバックされているので、ユーザーは存在しない
    val found = repository.findByEmail("john@example.com")
    found shouldBe null
}
```

#### JOINのテスト

```kotlin
test("ユーザーと注文をJOINできる") {
    val userRepository = UserRepositoryImpl()
    val orderRepository = OrderRepositoryImpl()
    
    // ユーザーを作成
    val user = userRepository.create("John Doe", "john@example.com")
    
    // 注文を作成
    orderRepository.create(user.id, "Product A", 2)
    orderRepository.create(user.id, "Product B", 1)
    
    // JOINで取得
    val userWithOrders = orderRepository.findUserWithOrders(user.id)
    
    userWithOrders shouldNotBe null
    userWithOrders!!.orders.size shouldBe 2
}
```

---

### 7.7 E2Eテスト

#### 完全なフローのテスト

```kotlin
@Testcontainers
class E2ETest : FunSpec({
    val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine")
    
    beforeSpec {
        postgres.start()
        
        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
        
        transaction {
            SchemaUtils.create(Users, Orders)
        }
    }
    
    afterSpec {
        transaction {
            SchemaUtils.drop(Users, Orders)
        }
        postgres.stop()
    }
    
    test("ユーザー作成から注文までのフロー") = testApplication {
        application {
            module()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        
        // 1. ユーザーを作成
        val createUserRequest = CreateUserRequest(
            name = "John Doe",
            email = "john@example.com"
        )
        
        val createUserResponse = client.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(createUserRequest)
        }
        
        createUserResponse.status shouldBe HttpStatusCode.Created
        val user = createUserResponse.body<User>()
        
        // 2. ユーザーを取得
        val getUserResponse = client.get("/api/v1/users/${user.id}")
        getUserResponse.status shouldBe HttpStatusCode.OK
        
        // 3. 注文を作成
        val createOrderRequest = CreateOrderRequest(
            userId = user.id,
            productId = "prod123",
            quantity = 2
        )
        
        val createOrderResponse = client.post("/api/v1/orders") {
            contentType(ContentType.Application.Json)
            setBody(createOrderRequest)
        }
        
        createOrderResponse.status shouldBe HttpStatusCode.Created
        val order = createOrderResponse.body<Order>()
        
        // 4. 注文履歴を取得
        val getOrdersResponse = client.get("/api/v1/orders?userId=${user.id}")
        getOrdersResponse.status shouldBe HttpStatusCode.OK
        
        val orders = getOrdersResponse.body<List<Order>>()
        orders.size shouldBe 1
        orders[0].id shouldBe order.id
    }
})
```

---

### 7.8 テストのベストプラクティス

#### テストの構造（AAA パターン）

```kotlin
test("テストの例") {
    // Arrange（準備）: テストデータとモックを用意
    val mockRepository = mockk<UserRepository>()
    val userService = UserService(mockRepository)
    val expectedUser = User("123", "John", "Doe", "john@example.com")
    coEvery { mockRepository.findById("123") } returns expectedUser
    
    // Act（実行）: テスト対象のメソッドを実行
    val result = userService.getUser("123")
    
    // Assert（検証）: 結果を検証
    result shouldBe expectedUser
    coVerify { mockRepository.findById("123") }
}
```

#### テストの命名

```kotlin
// ❌ 悪い例
test("test1") { }
test("userTest") { }

// ✅ 良い例
test("存在するユーザーIDでユーザーを取得できる") { }
test("存在しないユーザーIDはNotFoundExceptionを投げる") { }
test("無効なメールアドレスはValidationExceptionを投げる") { }
```

#### テストデータの管理

```kotlin
object TestData {
    fun createUser(
        id: String = "test-user-${UUID.randomUUID()}",
        name: String = "Test User",
        email: String = "test@example.com"
    ) = User(id, name, email)
    
    fun createOrder(
        id: String = "test-order-${UUID.randomUUID()}",
        userId: String = "user123",
        productName: String = "Test Product",
        quantity: Int = 1,
        status: String = "pending"
    ) = Order(id, userId, productName, quantity, status)
}

// 使用例
test("テストデータを使う") {
    val user = TestData.createUser(name = "John Doe")
    val order = TestData.createOrder(userId = user.id)
    
    // ...
}
```

#### テストのクリーンアップ

```kotlin
class DatabaseTest : FunSpec({
    lateinit var database: Database
    
    beforeSpec {
        // テスト開始前に1回実行
        database = setupDatabase()
    }
    
    afterSpec {
        // テスト終了後に1回実行
        cleanupDatabase()
    }
    
    beforeTest {
        // 各テスト前に実行
        clearTables()
    }
    
    afterTest {
        // 各テスト後に実行
        // 通常は不要（beforeTestでクリアするため）
    }
    
    test("テスト1") {
        // テストコード
    }
    
    test("テスト2") {
        // テストコード
    }
})
```

---

### 7.9 テストカバレッジ

#### JaCoCo の設定

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "1.9.22"
    id("jacoco")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/Application.kt",
                    "**/ApplicationKt.class",
                    "**/plugins/**"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()  // 80%以上
            }
        }
    }
}
```

#### カバレッジレポートの確認

```bash
# テストを実行してカバレッジレポートを生成
./gradlew test jacocoTestReport

# レポートを確認
open build/reports/jacoco/test/html/index.html
```

#### カバレッジ目標

| 層 | 目標カバレッジ |
|---|--------------|
| **ビジネスロジック** | 90%以上 |
| **Repository層** | 80%以上 |
| **Routing層** | 70%以上 |
| **プラグイン設定** | 50%以上（任意） |

---

## Chapter 8: CI/CD（GitHub Actions + AWS）

### 8.1 CI/CDの概要

#### CI/CD パイプライン

```
コミット
  ↓
ビルド
  ↓
ユニットテスト
  ↓
統合テスト
  ↓
Dockerイメージビルド
  ↓
ECRにプッシュ
  ↓
ECSにデプロイ
  ↓
スモークテスト
```

---

### 8.2 GitHub Actionsの基本

#### ワークフローファイルの作成

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_DB: testdb
          POSTGRES_USER: testuser
          POSTGRES_PASSWORD: testpass
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432
      
      redis:
        image: redis:7-alpine
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 6379:6379
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'
      
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      
      - name: Run tests
        run: ./gradlew test
        env:
          DB_URL: jdbc:postgresql://localhost:5432/testdb
          DB_USER: testuser
          DB_PASSWORD: testpass
          REDIS_HOST: localhost
          REDIS_PORT: 6379
      
      - name: Generate test coverage report
        run: ./gradlew jacocoTestReport
      
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          files: ./build/reports/jacoco/test/jacocoTestReport.xml
          flags: unittests
          name: codecov-umbrella
      
      - name: Check coverage threshold
        run: ./gradlew jacocoTestCoverageVerification
      
      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: build/test-results/
```

---

### 8.3 CD（継続的デプロイ）

#### デプロイワークフロー

```yaml
# .github/workflows/deploy.yml
name: Deploy to ECS

on:
  push:
    branches: [ main ]

env:
  AWS_REGION: ap-northeast-1
  ECR_REPOSITORY: ktor-bff
  ECS_CLUSTER: ktor-cluster
  ECS_SERVICE: ktor-service
  ECS_TASK_DEFINITION: ktor-task

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2
      
      - name: Build, tag, and push image to Amazon ECR
        id: build-image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          echo "image=$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG" >> $GITHUB_OUTPUT
      
      - name: Download task definition
        run: |
          aws ecs describe-task-definition \
            --task-definition $ECS_TASK_DEFINITION \
            --query taskDefinition > task-definition.json
      
      - name: Fill in the new image ID in the Amazon ECS task definition
        id: task-def
        uses: aws-actions/amazon-ecs-render-task-definition@v1
        with:
          task-definition: task-definition.json
          container-name: ktor-bff
          image: ${{ steps.build-image.outputs.image }}
      
      - name: Deploy Amazon ECS task definition
        uses: aws-actions/amazon-ecs-deploy-task-definition@v1
        with:
          task-definition: ${{ steps.task-def.outputs.task-definition }}
          service: ${{ env.ECS_SERVICE }}
          cluster: ${{ env.ECS_CLUSTER }}
          wait-for-service-stability: true
      
      - name: Smoke test
        run: |
          sleep 30
          curl -f https://api.example.com/health || exit 1
```

---

### 8.4 環境別デプロイ

#### ステージング環境

```yaml
# .github/workflows/deploy-staging.yml
name: Deploy to Staging

on:
  push:
    branches: [ develop ]

env:
  AWS_REGION: ap-northeast-1
  ECR_REPOSITORY: ktor-bff
  ECS_CLUSTER: ktor-cluster-staging
  ECS_SERVICE: ktor-service-staging

jobs:
  deploy:
    # ... 同様の設定
```

#### 本番環境（承認フロー付き）

```yaml
# .github/workflows/deploy-production.yml
name: Deploy to Production

on:
  release:
    types: [ published ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment:
      name: production
      url: https://api.example.com
    
    steps:
      # ... 同様の設定
```

**GitHub Environmentの設定**:
1. Settings → Environments → New environment
2. Environment name: `production`
3. Required reviewers: デプロイ承認者を追加
4. Wait timer: オプション（例: 5分）

---

### 8.5 シークレット管理

#### GitHub Secrets

**Settings → Secrets and variables → Actions**

```
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
DB_PASSWORD
JWT_SECRET
OAUTH_CLIENT_SECRET
```

#### ワークフローでの使用

```yaml
steps:
  - name: Run tests
    run: ./gradlew test
    env:
      DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
      JWT_SECRET: ${{ secrets.JWT_SECRET }}
```

---

### 8.6 マトリックステスト

#### 複数バージョンでのテスト

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [ '17', '21' ]
        postgres: [ '14', '15', '16' ]
    
    steps:
      - name: Set up JDK ${{ matrix.java }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
          distribution: 'temurin'
      
      - name: Start PostgreSQL ${{ matrix.postgres }}
        # ... PostgreSQL起動
      
      - name: Run tests
        run: ./gradlew test
```

---

### 8.7 キャッシング

#### Gradleキャッシュ

```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    java-version: '17'
    distribution: 'temurin'
    cache: 'gradle'  # ← Gradleキャッシュを有効化
```

#### Dockerレイヤーキャッシュ

```yaml
- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v3

- name: Build and push
  uses: docker/build-push-action@v5
  with:
    context: .
    push: true
    tags: ${{ steps.meta.outputs.tags }}
    cache-from: type=gha  # ← GitHubキャッシュから読み込み
    cache-to: type=gha,mode=max  # ← GitHubキャッシュに保存
```

---

### 8.8 通知

#### Slackへの通知

```yaml
- name: Notify Slack on success
  if: success()
  uses: slackapi/slack-github-action@v1
  with:
    channel-id: 'deployments'
    slack-message: |
      ✅ Deployment succeeded!
      Commit: ${{ github.sha }}
      Author: ${{ github.actor }}
  env:
    SLACK_BOT_TOKEN: ${{ secrets.SLACK_BOT_TOKEN }}

- name: Notify Slack on failure
  if: failure()
  uses: slackapi/slack-github-action@v1
  with:
    channel-id: 'deployments'
    slack-message: |
      ❌ Deployment failed!
      Commit: ${{ github.sha }}
      Author: ${{ github.actor }}
  env:
    SLACK_BOT_TOKEN: ${{ secrets.SLACK_BOT_TOKEN }}
```

---

### まとめ

この章で学んだこと:

1. ✅ **テスト戦略**
   - テストピラミッド
   - ユニット、統合、E2Eテスト

2. ✅ **Kotestの使用**
   - FunSpec、BehaviorSpec
   - マッチャー

3. ✅ **MockKでのモック**
   - 基本的なモック
   - コルーチン対応

4. ✅ **Ktor TestEngine**
   - APIエンドポイントのテスト
   - 認証付きテスト

5. ✅ **TestContainers**
   - PostgreSQLテスト
   - 統合テスト

6. ✅ **CI/CD**
   - GitHub Actions
   - ECRへのプッシュ
   - ECSへのデプロイ

---

### 次のステップ

次は**Part 8: AWSデプロイとモバイル最適化**で、本番環境へのデプロイと運用を学びます。

---

### 学習チェックリスト

- [ ] Kotestでユニットテストを書ける
- [ ] MockKでモックを作成できる
- [ ] Ktor TestEngineでAPIをテストできる
- [ ] TestContainersでデータベーステストができる
- [ ] JaCoCoでカバレッジを測定できる
- [ ] GitHub Actionsでビルドできる
- [ ] ECRにイメージをプッシュできる
- [ ] ECSにデプロイできる

全てチェックできたら、次のPartに進みましょう！
