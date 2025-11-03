# Part 0: Webアプリケーションの基礎知識

## Chapter 0: Webアプリケーションの仕組みとKtorの位置づけ

### 0.1 Webアプリケーションとは何か

#### クライアント-サーバーモデル

Webアプリケーションは、**クライアント**（モバイルアプリやWebブラウザ）と**サーバー**の間で通信を行うシステムです。

```
モバイルアプリ（クライアント）
        ↓ HTTPリクエスト
    インターネット
        ↓
  Webサーバー（サーバー）
        ↓ HTTPレスポンス
モバイルアプリ（クライアント）
```

#### サーバーの役割

サーバーは以下のような処理を行います：

1. **HTTPリクエストを受け取る**
   - クライアントからの要求を受信
   - URLとHTTPメソッド（GET, POST等）を解析

2. **ビジネスロジックを実行する**
   - リクエストに応じた処理を実行
   - データの検証、計算、変換など

3. **データベースから情報を取得する**
   - 必要なデータを永続化層から取得
   - データの作成、更新、削除

4. **HTTPレスポンスを返す**
   - 処理結果をJSON等の形式で返却
   - ステータスコード（200, 404, 500等）を設定

#### リクエストとレスポンスの例

**リクエスト（クライアント → サーバー）**
```http
GET /api/users/123 HTTP/1.1
Host: api.example.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Accept: application/json
```

**レスポンス（サーバー → クライアント）**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "123",
  "name": "John Doe",
  "email": "john@example.com"
}
```

---

### 0.2 Webフレームワークの役割

#### なぜフレームワークが必要か

Webアプリケーションを一から作るのは非常に大変です。フレームワークなしで書くと...

```kotlin
// 生のSocketプログラミング（こんなコードは書きたくない！）
val serverSocket = ServerSocket(8080)
while (true) {
    val socket = serverSocket.accept()
    val input = socket.getInputStream()
    val output = socket.getOutputStream()
    
    // HTTPリクエストを手動でパース...
    val requestLine = input.bufferedReader().readLine()
    val parts = requestLine.split(" ")
    val method = parts[0]
    val path = parts[1]
    
    // ルーティングを手動で実装...
    if (path == "/users" && method == "GET") {
        // ユーザー一覧を返す処理
    } else if (path.startsWith("/users/") && method == "GET") {
        // 特定のユーザーを返す処理
    }
    
    // JSONを手動でシリアライズ...
    val json = """{"id":"123","name":"John"}"""
    
    // HTTPレスポンスヘッダーを手動で構築...
    output.write("HTTP/1.1 200 OK\r\n".toByteArray())
    output.write("Content-Type: application/json\r\n".toByteArray())
    output.write("\r\n".toByteArray())
    output.write(json.toByteArray())
}
```

**Webフレームワークを使うと、これらが全て自動化されます！**

```kotlin
// Ktorなら簡単！
fun Application.module() {
    routing {
        get("/users") {
            val users = database.getAllUsers()
            call.respond(users)  // 自動的にJSONに変換される
        }
        
        get("/users/{id}") {
            val id = call.parameters["id"]
            val user = database.getUser(id)
            call.respond(user)
        }
    }
}
```

#### Webフレームワークが提供するもの

| 機能 | 説明 | 手動実装の難易度 |
|-----|------|--------------|
| **HTTPリクエストの受信と解析** | リクエストを構造化されたオブジェクトに変換 | ⭐⭐⭐⭐⭐ |
| **ルーティング** | URLとハンドラーのマッピング | ⭐⭐⭐⭐ |
| **JSONシリアライズ** | オブジェクト ⇄ JSON変換 | ⭐⭐⭐ |
| **セッション管理** | ユーザーの状態を保持 | ⭐⭐⭐⭐ |
| **認証・認可** | ユーザー認証とアクセス制御 | ⭐⭐⭐⭐⭐ |
| **エラーハンドリング** | 例外を適切なHTTPレスポンスに変換 | ⭐⭐⭐ |
| **ロギング** | リクエスト/レスポンスのログ記録 | ⭐⭐ |
| **CORS対応** | クロスオリジンリクエストの処理 | ⭐⭐⭐ |

フレームワークを使うことで、**ビジネスロジックに集中**できます。

---

### 0.3 主要なWebフレームワークの比較

#### Java/Kotlin エコシステムの主要フレームワーク

| フレームワーク | 特徴 | 単独動作 | 学習曲線 | 適している用途 |
|------------|------|---------|---------|-------------|
| **Ktor** | 軽量、コルーチンネイティブ | ✅ はい | 緩やか | BFF、API、マイクロサービス |
| **Spring Boot** | 多機能、エコシステム豊富 | ✅ はい | 急（機能が多い） | エンタープライズ、全般 |
| **Micronaut** | 起動速度重視、コンパイル時DI | ✅ はい | やや急 | マイクロサービス、Serverless |
| **Quarkus** | GraalVM対応、ネイティブコンパイル | ✅ はい | やや急 | Kubernetes、Serverless |

**重要なポイント**: これらは**全て単独で動作する完全なフレームワーク**です。他のフレームワークをインストールする必要はありません。

#### 他の言語のフレームワーク（参考）

| 言語 | フレームワーク | 特徴 |
|-----|------------|------|
| Python | Django, Flask, FastAPI | 簡潔な構文、豊富なライブラリ |
| JavaScript | Express.js, NestJS | Node.js上で動作、非同期処理が得意 |
| Go | Gin, Echo | 高速、シンプル |
| Ruby | Ruby on Rails | 規約重視、開発速度が速い |

---

### 0.4 Ktorの特徴と位置づけ

#### Ktorとは

**Ktor（ケイター）**は、JetBrains社が開発したKotlin専用のWebフレームワークです。

**主な特徴**:
- ✅ **完全に独立して動作**（他のフレームワーク不要）
- ✅ **組み込みサーバーを内蔵**（Netty/CIO/Jetty）
- ✅ **Kotlin Coroutines完全対応**（非同期処理がシンプル）
- ✅ **軽量で高速**（起動時間1-3秒、メモリ使用量が少ない）
- ✅ **プラグインシステム**（必要な機能だけを追加）
- ✅ **型安全**（Kotlinの型システムを活用）

#### Ktorの動作原理

```
Ktorアプリケーション（単一のJARファイル）
├── 組み込みサーバー（Netty/CIO/Jetty）← これが内蔵されている！
├── ルーティングエンジン
├── プラグインシステム
│   ├── ContentNegotiation（JSON処理）
│   ├── Authentication（認証）
│   ├── CORS（クロスオリジン対応）
│   └── その他のプラグイン
└── ビジネスロジック
    ├── ルーティング
    ├── サービス層
    └── データアクセス層
```

**重要**: Ktorは**自己完結型**です。Spring Bootも、Tomcatも、何も追加でインストールする必要がありません。

#### 最小限のKtorアプリケーション

```kotlin
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8080) {
        routing {
            get("/") {
                call.respondText("Hello from Ktor!")
            }
            
            get("/users/{id}") {
                val id = call.parameters["id"]
                call.respondText("User ID: $id")
            }
        }
    }.start(wait = true)
}
```

このコードを実行するだけでWebサーバーが起動します！

```bash
# 実行
./gradlew run

# 別のターミナルでテスト
curl http://localhost:8080/
# → Hello from Ktor!

curl http://localhost:8080/users/123
# → User ID: 123
```

---

### 0.5 「組み込みサーバー」とは

#### 従来のJavaアプリケーション（昔の方式）

昔は、Webアプリケーションを動かすために**別途サーバーソフトウェア**が必要でした。

```
Apache Tomcatサーバー（別途インストールが必要）
  ├── アプリA.war
  ├── アプリB.war
  └── アプリC.war
```

**手順**:
1. Tomcatをダウンロード・インストール
2. アプリケーションをWARファイルにパッケージング
3. WARファイルをTomcatの`webapps`ディレクトリに配置
4. Tomcatを起動
5. やっとアプリケーションが動く

**問題点**:
- セットアップが複雑
- 開発環境と本番環境で設定が異なる
- バージョン管理が難しい
- 1つのTomcatで複数アプリを動かすと依存関係が競合

#### 現代のフレームワーク（Ktor, Spring Boot等）

現代のフレームワークは**サーバーエンジンを内蔵**しています。

```
アプリケーション.jar（これだけ！）
  └── サーバーエンジン（内蔵）← これがポイント！
```

**手順**:
1. アプリケーションをJARファイルにパッケージング
2. `java -jar app.jar` で起動
3. 完了！

**メリット**:
- ✅ セットアップが簡単
- ✅ 開発環境と本番環境が同じ
- ✅ バージョン管理が容易（JARファイル1つだけ）
- ✅ Docker化が簡単
- ✅ 依存関係の競合がない

#### Ktorで選べるサーバーエンジン

Ktorは複数のサーバーエンジンに対応しています。

```kotlin
// Nettyエンジン（デフォルト、最も人気）
embeddedServer(Netty, port = 8080) { ... }

// CIOエンジン（Kotlin純正、軽量）
embeddedServer(CIO, port = 8080) { ... }

// Jettyエンジン（Java老舗、安定性重視）
embeddedServer(Jetty, port = 8080) { ... }
```

**推奨**: 特別な理由がなければ**Netty**を使用してください。最も最適化されており、パフォーマンスが良好です。

---

### 0.6 Ktorアプリケーションのライフサイクル

#### 1. アプリケーション起動

```kotlin
fun main() {
    embeddedServer(Netty, port = 8080) {
        module()  // ← プラグインとルーティングを設定
    }.start(wait = true)
}
```

#### 2. モジュールの設定

```kotlin
fun Application.module() {
    // プラグインのインストール
    install(ContentNegotiation) {
        json()  // JSON処理を有効化
    }
    
    install(CallLogging) {
        level = Level.INFO  // ログレベル設定
    }
    
    install(CORS) {
        allowHost("*")  // CORS設定
    }
    
    // ルーティングの設定
    configureRouting()
}
```

#### 3. リクエストの処理フロー

```
1. HTTPリクエスト受信
        ↓
2. Ktorのルーティングエンジン
   - URLとHTTPメソッドでハンドラーを検索
        ↓
3. プラグインパイプライン
   - CallLogging（ログ記録）
   - Authentication（認証チェック）
   - ContentNegotiation（リクエストボディのパース）
        ↓
4. ハンドラー実行
   - ビジネスロジック
   - データベースアクセス
        ↓
5. プラグインパイプライン（戻り）
   - ContentNegotiation（レスポンスのシリアライズ）
   - CallLogging（レスポンスログ）
        ↓
6. HTTPレスポンス送信
```

#### 4. コルーチンでの非同期処理

Ktorの大きな特徴は、**全てのハンドラーがコルーチンで実行される**ことです。

```kotlin
routing {
    get("/users/{id}") {
        // このブロック全体がコルーチン内で実行される
        val id = call.parameters["id"]!!
        
        // suspend関数を直接呼べる（非同期処理）
        val user = userService.getUser(id)  // suspend fun
        val orders = orderService.getOrders(id)  // suspend fun
        
        // レスポンス
        call.respond(mapOf(
            "user" to user,
            "orders" to orders
        ))
    }
}
```

**コルーチンの利点**:
- 非同期処理でもコードが読みやすい
- スレッドをブロックしない（高いスループット）
- 複数の処理を並列実行しやすい

```kotlin
// 複数のAPIを並列で呼び出す
suspend fun getAggregatedData(userId: String) = coroutineScope {
    val userDeferred = async { userClient.getUser(userId) }
    val ordersDeferred = async { orderClient.getOrders(userId) }
    val recommendationsDeferred = async { recClient.getRecommendations(userId) }
    
    AggregatedData(
        user = userDeferred.await(),
        orders = ordersDeferred.await(),
        recommendations = recommendationsDeferred.await()
    )
}
```

---

### 0.7 Fat JAR（Uber JAR）とは

#### 通常のJAR

通常のJARファイルには、**自分のコードだけ**が含まれています。

```
app.jar
├── com/example/Main.class
├── com/example/User.class
├── com/example/UserService.class
└── META-INF/MANIFEST.MF
```

**問題**: 実行時に依存ライブラリ（Ktor、PostgreSQLドライバ等）が別途必要

```bash
# クラスパスに依存ライブラリを全て指定する必要がある
java -cp "app.jar:ktor-server-core.jar:ktor-server-netty.jar:..." com.example.MainKt
```

#### Fat JAR（全部入り）

Fat JARは、**全ての依存関係を1つのJARファイルにまとめたもの**です。

```
app-all.jar（約50-80MB）
├── com/example/Main.class              ← 自分のコード
├── com/example/User.class
├── io/ktor/server/...                  ← Ktorのコード
├── kotlinx/coroutines/...              ← コルーチンライブラリ
├── org/postgresql/...                  ← PostgreSQLドライバ
├── com/zaxxer/hikari/...               ← HikariCP
├── kotlinx/serialization/...           ← kotlinx.serialization
└── META-INF/MANIFEST.MF
```

**メリット**:
- ✅ 1つのJARファイルだけで動作
- ✅ 実行が簡単（`java -jar app-all.jar`だけ）
- ✅ デプロイが簡単（ファイル1つをコピーするだけ）
- ✅ Dockerイメージが作りやすい

#### Gradleでの生成

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "3.0.0"
}

application {
    mainClass.set("com.example.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("app-all.jar")
    }
}
```

**ビルドコマンド**:
```bash
./gradlew buildFatJar
# → build/libs/app-all.jar が生成される
```

**実行**:
```bash
java -jar build/libs/app-all.jar
```

これだけで、Webサーバーが起動します！

#### Dockerfileでの使用

Fat JARは、Dockerイメージを作る際に非常に便利です。

```dockerfile
# ビルドステージ（開発ツールが入った大きなイメージ）
FROM gradle:8.5-jdk17 AS build
COPY . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle buildFatJar --no-daemon  # ← Fat JAR作成

# 実行ステージ（JREだけの小さなイメージ）
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*-all.jar /app/app.jar

# ヘルスチェック
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -q --spider http://localhost:8080/health || exit 1

EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]  # ← これだけで起動！
```

**最終的なイメージサイズ**:
- ビルドイメージ: 約600MB（開発時のみ使用、破棄される）
- 実行イメージ: 約120-150MB（本番で使用）

---

### 0.8 KtorとSpring Bootの違い

どちらも**完全に独立したWebフレームワーク**ですが、設計思想や特徴が異なります。

#### 詳細比較

| 観点 | Ktor | Spring Boot |
|-----|------|-------------|
| **単独動作** | ✅ はい | ✅ はい |
| **開発元** | JetBrains | Pivotal/VMware |
| **言語** | Kotlin専用 | Java/Kotlin/Groovy |
| **学習曲線** | 緩やか | 急（機能が多い） |
| **非同期処理** | コルーチンネイティブ | WebFlux（複雑） |
| **起動速度** | 速い（1-3秒） | 遅め（5-15秒） |
| **メモリ使用量** | 小さい（128-512MB） | 大きい（512MB-2GB） |
| **Fat JARサイズ** | 50-80MB | 100-200MB |
| **エコシステム** | 小さい（成長中） | 巨大（成熟） |
| **依存性注入** | シンプル（Koin推奨） | 複雑（Spring DI） |
| **設定方法** | コード中心 | アノテーション/YAML |
| **適している用途** | BFF、API、マイクロサービス | エンタープライズ全般 |
| **データアクセス** | Exposed、JDBC | Spring Data JPA |
| **コミュニティ** | 小さい | 非常に大きい |

#### コード比較

**Hello Worldアプリ**

Ktor:
```kotlin
fun main() {
    embeddedServer(Netty, port = 8080) {
        routing {
            get("/") {
                call.respondText("Hello, Ktor!")
            }
        }
    }.start(wait = true)
}
```

Spring Boot:
```kotlin
@SpringBootApplication
@RestController
class Application {
    @GetMapping("/")
    fun hello() = "Hello, Spring Boot!"
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
```

**どちらも単純ですが、Ktorの方がより明示的です。**

#### 認証の実装

Ktor:
```kotlin
install(Authentication) {
    jwt("auth-jwt") {
        verifier(jwkProvider, issuer)
        validate { credential ->
            if (credential.payload.getClaim("user_id").asString() != "") {
                JWTPrincipal(credential.payload)
            } else null
        }
    }
}

routing {
    authenticate("auth-jwt") {
        get("/profile") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal!!.payload.getClaim("user_id").asString()
            call.respond(mapOf("userId" to userId))
        }
    }
}
```

Spring Boot:
```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
        http
            .authorizeRequests()
            .antMatchers("/profile").authenticated()
            .and()
            .oauth2ResourceServer()
            .jwt()
    }
}

@RestController
class ProfileController {
    @GetMapping("/profile")
    fun getProfile(@AuthenticationPrincipal jwt: Jwt): Map<String, String> {
        return mapOf("userId" to jwt.subject)
    }
}
```

#### どちらを選ぶべきか

**Ktorを選ぶべき場合**:
- ✅ Kotlinで開発したい
- ✅ 軽量で高速なAPIが必要
- ✅ BFFやマイクロサービスを構築する
- ✅ 非同期処理（コルーチン）を活用したい
- ✅ シンプルなアーキテクチャが好き
- ✅ 学習曲線を緩やかにしたい

**Spring Bootを選ぶべき場合**:
- ✅ 大規模なエンタープライズアプリケーション
- ✅ 豊富なエコシステムが必要
- ✅ Javaとの互換性が重要
- ✅ Spring Data JPAなどの高度な機能が必要
- ✅ 既存のSpringの知識を活用したい
- ✅ 成熟したコミュニティのサポートが欲しい

**このガイドでは、モバイルBFFに最適なKtorを使用します。**

---

### 0.9 実際に動かしてみる

実際にKtorアプリケーションを作って動かしてみましょう。

#### ステップ1: プロジェクトの作成

**方法A: Ktor Project Generator（推奨）**

1. ブラウザで https://start.ktor.io/ にアクセス
2. 以下を設定:
   - Project: Gradle Kotlin
   - Engine: Netty
   - Configuration: HOCON
   - Build System: Gradle Kotlin DSL
   - Ktor version: 3.0.0 (最新)
3. プラグインを選択:
   - Routing
   - Content Negotiation
   - kotlinx.serialization
4. "Generate Project"をクリック
5. ダウンロードしたZIPファイルを解凍

**方法B: 手動作成**

```bash
mkdir ktor-hello-world
cd ktor-hello-world
```

`build.gradle.kts`を作成:
```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "3.0.0"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "com.example"
version = "1.0.0"

application {
    mainClass.set("com.example.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

ktor {
    fatJar {
        archiveFileName.set("app-all.jar")
    }
}
```

#### ステップ2: アプリケーションコードの作成

`src/main/kotlin/com/example/Application.kt`:
```kotlin
package com.example

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class User(val id: Int, val name: String, val email: String)

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // プラグインの設定
    install(ContentNegotiation) {
        json()
    }
    
    // ルーティング
    routing {
        get("/") {
            call.respondText("Hello from Ktor!")
        }
        
        get("/users") {
            val users = listOf(
                User(1, "Alice", "alice@example.com"),
                User(2, "Bob", "bob@example.com"),
                User(3, "Charlie", "charlie@example.com")
            )
            call.respond(users)
        }
        
        get("/users/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondText("Invalid user ID", status = io.ktor.http.HttpStatusCode.BadRequest)
                return@get
            }
            
            val user = User(id, "User $id", "user$id@example.com")
            call.respond(user)
        }
    }
}
```

#### ステップ3: 実行

```bash
# Gradleでビルド・実行
./gradlew run
```

出力:
```
2024-11-03 10:00:00.123 [main] INFO  ktor.application - Responding at http://0.0.0.0:8080
```

#### ステップ4: テスト

別のターミナルで:

```bash
# ルートエンドポイント
curl http://localhost:8080/
# → Hello from Ktor!

# ユーザー一覧
curl http://localhost:8080/users
# → [{"id":1,"name":"Alice","email":"alice@example.com"},...]

# 特定のユーザー
curl http://localhost:8080/users/1
# → {"id":1,"name":"User 1","email":"user1@example.com"}

# 不正なID
curl http://localhost:8080/users/abc
# → Invalid user ID
```

#### ステップ5: Fat JARの作成と実行

```bash
# Fat JARのビルド
./gradlew buildFatJar

# 生成されたJARの確認
ls -lh build/libs/
# → app-all.jar  約50MB

# JARファイルの実行
java -jar build/libs/app-all.jar
```

**これだけで、Webサーバーが起動します！**

---

### まとめ

この章で学んだこと:

1. ✅ **Webアプリケーションの基本**
   - クライアント-サーバーモデル
   - HTTPリクエスト・レスポンス

2. ✅ **Webフレームワークの役割**
   - HTTPの複雑さを隠蔽
   - ビジネスロジックに集中できる

3. ✅ **Ktorの位置づけ**
   - 完全に独立したフレームワーク
   - Spring Bootなどの他のフレームワークは不要
   - 組み込みサーバーを内蔵

4. ✅ **組み込みサーバー**
   - 外部のTomcat等は不要
   - `java -jar app.jar`で起動

5. ✅ **Fat JAR**
   - 全ての依存関係を含む
   - デプロイが簡単

6. ✅ **実際の動作確認**
   - 最小限のKtorアプリを作成・実行

---

### 次のステップ

次は**Part 1: Ktorを理解するための最小限のインフラ知識**で、HTTPの詳細とネットワークの基礎を学びます。

---

### 学習チェックリスト

この章を理解できたか確認しましょう:

- [ ] Webアプリケーションのクライアント-サーバーモデルを説明できる
- [ ] Webフレームワークの役割を理解している
- [ ] Ktorが単独で動作する完全なフレームワークであることを理解している
- [ ] 組み込みサーバーの概念を理解している
- [ ] Fat JARとは何かを説明できる
- [ ] `java -jar app.jar`だけで起動できることを理解している
- [ ] Spring Bootなどの他フレームワークが不要であることを理解している
- [ ] 最小限のKtorアプリケーションを作成・実行できる

全てチェックできたら、次のPartに進みましょう！
