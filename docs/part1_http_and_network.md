# Part 1: Ktorを理解するための最小限のインフラ知識

## Chapter 1: HTTPとネットワークの本質

### 1.1 HTTPリクエスト・レスポンスの詳細

#### HTTPリクエストの構造

HTTPリクエストは、以下の3つの部分で構成されています：

```
1. リクエストライン
2. ヘッダー
3. ボディ（オプション）
```

**実際のHTTPリクエストの例**:

```http
POST /api/users HTTP/1.1                    ← リクエストライン
Host: api.example.com                       ← ヘッダー開始
Content-Type: application/json
Content-Length: 58
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Accept: application/json
                                            ← 空行（ヘッダーとボディの区切り）
{"name":"John Doe","email":"john@example.com"}  ← ボディ
```

**1. リクエストライン**

```
メソッド パス プロトコルバージョン
POST /api/users HTTP/1.1
```

- **メソッド**: 実行したい操作（GET, POST, PUT, DELETE等）
- **パス**: アクセスしたいリソース（`/api/users`）
- **プロトコルバージョン**: HTTP/1.1, HTTP/2等

**2. ヘッダー**

ヘッダーはキー: 値の形式で、リクエストのメタ情報を含みます。

| ヘッダー | 説明 | 例 |
|---------|------|-----|
| **Host** | アクセス先のホスト（必須） | `api.example.com` |
| **Content-Type** | ボディのデータ形式 | `application/json` |
| **Content-Length** | ボディのバイト数 | `58` |
| **Authorization** | 認証情報 | `Bearer token...` |
| **Accept** | 受け入れ可能なレスポンス形式 | `application/json` |
| **User-Agent** | クライアント情報 | `Android/12` |

**3. ボディ**

POST、PUT、PATCHリクエストで送信するデータ。

```json
{
  "name": "John Doe",
  "email": "john@example.com"
}
```

#### HTTPレスポンスの構造

HTTPレスポンスも同様に3つの部分で構成されています：

```
1. ステータスライン
2. ヘッダー
3. ボディ
```

**実際のHTTPレスポンスの例**:

```http
HTTP/1.1 201 Created                        ← ステータスライン
Content-Type: application/json              ← ヘッダー開始
Content-Length: 89
Location: /api/users/123
Date: Mon, 03 Nov 2025 10:00:00 GMT
                                            ← 空行
{"id":"123","name":"John Doe","email":"john@example.com"}  ← ボディ
```

**1. ステータスライン**

```
プロトコルバージョン ステータスコード ステータステキスト
HTTP/1.1 201 Created
```

**2. レスポンスヘッダー**

| ヘッダー | 説明 | 例 |
|---------|------|-----|
| **Content-Type** | レスポンスのデータ形式 | `application/json` |
| **Content-Length** | ボディのバイト数 | `89` |
| **Location** | 作成されたリソースのURL | `/api/users/123` |
| **Date** | レスポンスの日時 | `Mon, 03 Nov 2025...` |
| **Cache-Control** | キャッシュの制御 | `no-cache` |
| **Set-Cookie** | Cookieの設定 | `session=abc123` |

**3. レスポンスボディ**

クライアントに返すデータ。

```json
{
  "id": "123",
  "name": "John Doe",
  "email": "john@example.com"
}
```

---

### 1.2 HTTPメソッドの使い分け

HTTPメソッドは、リソースに対して実行する操作を示します。

#### 主要なHTTPメソッド

| メソッド | 用途 | 冪等性 | 安全 | ボディ |
|---------|------|-------|------|-------|
| **GET** | リソースの取得 | ✅ はい | ✅ はい | なし |
| **POST** | リソースの作成 | ❌ いいえ | ❌ いいえ | あり |
| **PUT** | リソースの更新/置換 | ✅ はい | ❌ いいえ | あり |
| **PATCH** | リソースの部分更新 | ❌ いいえ | ❌ いいえ | あり |
| **DELETE** | リソースの削除 | ✅ はい | ❌ いいえ | なし |

**冪等性（Idempotence）**: 同じリクエストを複数回実行しても、結果が同じになる性質
**安全（Safe）**: リソースを変更しない性質

#### GET - リソースの取得

```http
GET /api/users/123 HTTP/1.1
Host: api.example.com
```

**用途**:
- ユーザー情報の取得
- 商品一覧の取得
- 検索結果の取得

**特徴**:
- クエリパラメータでフィルタリング: `/api/users?role=admin&page=1`
- キャッシュ可能
- ブックマーク可能

**Ktorでの実装**:
```kotlin
get("/api/users/{id}") {
    val id = call.parameters["id"]!!
    val user = userService.getUser(id)
    call.respond(user)
}

get("/api/users") {
    val role = call.request.queryParameters["role"]
    val users = userService.getUsers(role)
    call.respond(users)
}
```

#### POST - リソースの作成

```http
POST /api/users HTTP/1.1
Host: api.example.com
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com"
}
```

**用途**:
- 新規ユーザーの作成
- 注文の作成
- コメントの投稿

**特徴**:
- 冪等ではない（同じリクエストで複数のリソースが作成される）
- レスポンスに`Location`ヘッダーで作成されたリソースのURLを返す

**Ktorでの実装**:
```kotlin
post("/api/users") {
    val userRequest = call.receive<CreateUserRequest>()
    val user = userService.createUser(userRequest)
    
    call.response.header(
        "Location", 
        "/api/users/${user.id}"
    )
    call.respond(HttpStatusCode.Created, user)
}
```

#### PUT - リソースの更新/置換

```http
PUT /api/users/123 HTTP/1.1
Host: api.example.com
Content-Type: application/json

{
  "name": "Jane Doe",
  "email": "jane@example.com"
}
```

**用途**:
- ユーザー情報の完全更新
- 設定の置換

**特徴**:
- 冪等（同じリクエストを複数回実行しても結果は同じ）
- リソース全体を置き換える

**Ktorでの実装**:
```kotlin
put("/api/users/{id}") {
    val id = call.parameters["id"]!!
    val userRequest = call.receive<UpdateUserRequest>()
    val user = userService.updateUser(id, userRequest)
    call.respond(user)
}
```

#### PATCH - リソースの部分更新

```http
PATCH /api/users/123 HTTP/1.1
Host: api.example.com
Content-Type: application/json

{
  "email": "newemail@example.com"
}
```

**用途**:
- ユーザーのメールアドレスだけを変更
- ステータスの更新

**特徴**:
- リソースの一部だけを更新
- PUTと異なり、指定したフィールドだけが更新される

**Ktorでの実装**:
```kotlin
patch("/api/users/{id}") {
    val id = call.parameters["id"]!!
    val patchRequest = call.receive<PatchUserRequest>()
    val user = userService.patchUser(id, patchRequest)
    call.respond(user)
}
```

#### DELETE - リソースの削除

```http
DELETE /api/users/123 HTTP/1.1
Host: api.example.com
```

**用途**:
- ユーザーの削除
- 注文のキャンセル

**特徴**:
- 冪等（既に削除されているリソースを削除しても安全）
- レスポンスボディは通常空、または削除されたリソース情報

**Ktorでの実装**:
```kotlin
delete("/api/users/{id}") {
    val id = call.parameters["id"]!!
    userService.deleteUser(id)
    call.respond(HttpStatusCode.NoContent)
}
```

---

### 1.3 HTTPステータスコード

HTTPステータスコードは、リクエストの処理結果を示します。

#### ステータスコードの分類

| 範囲 | 分類 | 意味 |
|-----|------|------|
| **1xx** | Informational | 情報レスポンス |
| **2xx** | Success | 成功 |
| **3xx** | Redirection | リダイレクト |
| **4xx** | Client Error | クライアントエラー |
| **5xx** | Server Error | サーバーエラー |

#### よく使う2xx成功コード

| コード | 名前 | 用途 |
|-------|------|------|
| **200** | OK | リクエスト成功（GET, PUT, PATCHで使用） |
| **201** | Created | リソース作成成功（POSTで使用） |
| **204** | No Content | 成功したがレスポンスボディなし（DELETEで使用） |

**Ktorでの使用例**:
```kotlin
// 200 OK
get("/api/users/{id}") {
    val user = userService.getUser(id)
    call.respond(HttpStatusCode.OK, user)  // または call.respond(user)
}

// 201 Created
post("/api/users") {
    val user = userService.createUser(request)
    call.respond(HttpStatusCode.Created, user)
}

// 204 No Content
delete("/api/users/{id}") {
    userService.deleteUser(id)
    call.respond(HttpStatusCode.NoContent)
}
```

#### よく使う4xxクライアントエラー

| コード | 名前 | 用途 | 対処法 |
|-------|------|------|--------|
| **400** | Bad Request | リクエストが不正 | リクエストを修正 |
| **401** | Unauthorized | 認証が必要 | 認証情報を提供 |
| **403** | Forbidden | アクセス権限なし | 権限を取得 |
| **404** | Not Found | リソースが見つからない | URLを確認 |
| **409** | Conflict | リソースの競合 | 競合を解決 |
| **422** | Unprocessable Entity | バリデーションエラー | データを修正 |
| **429** | Too Many Requests | レート制限超過 | リクエストを減らす |

**Ktorでの使用例**:
```kotlin
// 400 Bad Request
get("/api/users/{id}") {
    val id = call.parameters["id"]?.toIntOrNull()
    if (id == null) {
        call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("Invalid user ID")
        )
        return@get
    }
    // ...
}

// 401 Unauthorized
authenticate("auth-jwt") {
    get("/api/profile") {
        val principal = call.principal<JWTPrincipal>()
            ?: return@get call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("Authentication required")
            )
        // ...
    }
}

// 404 Not Found
get("/api/users/{id}") {
    val user = userService.getUser(id)
        ?: return@get call.respond(
            HttpStatusCode.NotFound,
            ErrorResponse("User not found")
        )
    call.respond(user)
}

// 422 Unprocessable Entity
post("/api/users") {
    val request = call.receive<CreateUserRequest>()
    
    val errors = validateUser(request)
    if (errors.isNotEmpty()) {
        call.respond(
            HttpStatusCode.UnprocessableEntity,
            ValidationErrorResponse(errors)
        )
        return@post
    }
    // ...
}
```

#### よく使う5xxサーバーエラー

| コード | 名前 | 用途 |
|-------|------|------|
| **500** | Internal Server Error | サーバー内部エラー |
| **502** | Bad Gateway | ゲートウェイエラー（上流サーバーからの不正なレスポンス） |
| **503** | Service Unavailable | サービス利用不可（メンテナンス中等） |
| **504** | Gateway Timeout | ゲートウェイタイムアウト |

**Ktorでの使用例**:
```kotlin
// StatusPagesプラグインでのエラーハンドリング
install(StatusPages) {
    exception<Throwable> { call, cause ->
        logger.error("Unhandled exception", cause)
        call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse("Internal server error")
        )
    }
    
    exception<ServiceUnavailableException> { call, cause ->
        call.respond(
            HttpStatusCode.ServiceUnavailable,
            ErrorResponse("Service temporarily unavailable")
        )
    }
}
```

---

### 1.4 モバイルBFFで重要なHTTPヘッダー

#### Authorization - 認証情報

APIへのアクセス権限を証明するためのヘッダー。

**Bearer Token方式**:
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Ktorでの検証**:
```kotlin
install(Authentication) {
    jwt("auth-jwt") {
        verifier(jwkProvider, issuer)
        validate { credential ->
            // トークンを検証
            if (credential.payload.getClaim("user_id").asString() != "") {
                JWTPrincipal(credential.payload)
            } else null
        }
    }
}

// 使用
authenticate("auth-jwt") {
    get("/api/profile") {
        val principal = call.principal<JWTPrincipal>()!!
        val userId = principal.payload.getClaim("user_id").asString()
        // ...
    }
}
```

#### Content-Type - コンテンツの種類

リクエスト/レスポンスのボディのデータ形式を指定。

**よく使う値**:

| Content-Type | 用途 |
|-------------|------|
| `application/json` | JSON（最も一般的） |
| `application/x-www-form-urlencoded` | フォームデータ |
| `multipart/form-data` | ファイルアップロード |
| `text/plain` | プレーンテキスト |
| `image/jpeg`, `image/png` | 画像 |

**Ktorでの設定**:
```kotlin
install(ContentNegotiation) {
    json(Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    })
}

// レスポンス時は自動的にContent-Typeが設定される
get("/api/users") {
    val users = userService.getUsers()
    call.respond(users)  // Content-Type: application/json
}
```

#### CORSヘッダー - クロスオリジンリクエスト

異なるドメインからのAPIアクセスを制御。

**主要なCORSヘッダー**:

| ヘッダー | 説明 | 例 |
|---------|------|-----|
| `Access-Control-Allow-Origin` | 許可するオリジン | `https://app.example.com` |
| `Access-Control-Allow-Methods` | 許可するメソッド | `GET, POST, PUT, DELETE` |
| `Access-Control-Allow-Headers` | 許可するヘッダー | `Authorization, Content-Type` |
| `Access-Control-Max-Age` | プリフライトのキャッシュ時間 | `3600` |

**Ktorでの設定**:
```kotlin
install(CORS) {
    // 特定のオリジンを許可
    allowHost("app.example.com", schemes = listOf("https"))
    
    // メソッドを許可
    allowMethod(HttpMethod.GET)
    allowMethod(HttpMethod.POST)
    allowMethod(HttpMethod.PUT)
    allowMethod(HttpMethod.DELETE)
    
    // ヘッダーを許可
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    
    // 認証情報（Cookie等）を許可
    allowCredentials = true
    
    // プリフライトリクエストのキャッシュ時間
    maxAgeInSeconds = 3600
}
```

#### Cookie - セッション管理

サーバーがクライアントに状態を保存するための仕組み。

**Cookieの属性**:

| 属性 | 説明 | 推奨値（モバイルBFF） |
|-----|------|-------------------|
| `HttpOnly` | JavaScriptからアクセス不可 | `true` |
| `Secure` | HTTPS通信のみ | `true` |
| `SameSite` | CSRF対策 | `Strict` or `Lax` |
| `Max-Age` | 有効期限（秒） | `3600`（1時間） |
| `Path` | Cookieが有効なパス | `/` |

**Ktorでの設定**:
```kotlin
install(Sessions) {
    cookie<UserSession>("SESSION") {
        cookie.httpOnly = true
        cookie.secure = true
        cookie.extensions["SameSite"] = "Strict"
        cookie.maxAgeInSeconds = 3600
        cookie.path = "/"
    }
}

// セッションの設定
get("/auth/callback") {
    // OAuth認証後
    val sessionId = UUID.randomUUID().toString()
    call.sessions.set(UserSession(sessionId, userId))
    call.respondRedirect("/")
}

// セッションの取得
get("/api/profile") {
    val session = call.sessions.get<UserSession>()
        ?: return@get call.respond(HttpStatusCode.Unauthorized)
    
    val user = userService.getUser(session.userId)
    call.respond(user)
}
```

---

### 1.5 HTTPS/TLSの基礎

#### HTTPとHTTPSの違い

| 項目 | HTTP | HTTPS |
|-----|------|-------|
| **ポート** | 80 | 443 |
| **暗号化** | なし | あり（TLS） |
| **証明書** | 不要 | 必要 |
| **安全性** | 低い | 高い |
| **速度** | わずかに速い | わずかに遅い |

**HTTPの問題点**:
- 通信内容が平文（盗聴可能）
- 改ざん検知ができない
- なりすまし防止ができない

**HTTPSの利点**:
- ✅ **暗号化**: 通信内容が保護される
- ✅ **完全性**: 改ざんを検知できる
- ✅ **認証**: サーバーの正当性を確認できる

#### SSL/TLS証明書

SSL/TLS証明書は、サーバーの身元を証明し、暗号化通信を可能にします。

**証明書に含まれる情報**:
- ドメイン名（例: api.example.com）
- 発行者（認証局）
- 有効期限
- 公開鍵

**証明書の種類**:

| 種類 | 検証レベル | 取得時間 | 用途 |
|-----|----------|---------|------|
| **DV証明書** | ドメインのみ | 数分～数時間 | 個人サイト、テスト |
| **OV証明書** | 組織情報 | 数日 | 企業サイト |
| **EV証明書** | 厳格な組織検証 | 数週間 | 金融機関等 |

**無料証明書**: Let's Encryptが無料のDV証明書を提供

#### モバイルBFFでのHTTPS設定

**重要**: Ktorアプリケーション自体でHTTPSを設定する必要は**ありません**。

```
モバイルアプリ
    ↓ HTTPS
AWS ALB（HTTPS終端）← ここで証明書を設定
    ↓ HTTP
ECS/Fargate（Ktorアプリ）← HTTPで問題なし
```

**理由**:
- ALBでHTTPSを終端（TLS Termination）
- ALB ↔ Ktorアプリ間はAWS内部ネットワーク（安全）
- 証明書管理はAWS Certificate Manager（ACM）が担当
- Ktorアプリはシンプルに保てる

**開発環境での注意**:
- ローカル開発ではHTTPで問題なし（`http://localhost:8080`）
- モバイルアプリから実サーバーへのアクセスは必ずHTTPS

---

### 1.6 DNS・IPアドレス・ポートの基礎

#### ドメイン名とIPアドレス

**ドメイン名**: 人間が読みやすい名前
- 例: `api.example.com`

**IPアドレス**: コンピュータが使う数値
- IPv4: `192.0.2.1`
- IPv6: `2001:0db8:85a3:0000:0000:8a2e:0370:7334`

#### DNS（Domain Name System）

DNSは、ドメイン名をIPアドレスに変換します。

```
1. モバイルアプリ: "api.example.comに接続したい"
2. DNS: "api.example.comのIPアドレスは203.0.113.10です"
3. モバイルアプリ: "203.0.113.10に接続します"
```

**DNS解決のプロセス**:
```
モバイルアプリ
    ↓ api.example.com?
DNSリゾルバ（キャッシュサーバー）
    ↓ キャッシュなし
ルートDNSサーバー
    ↓ .comサーバーを教える
TLD DNSサーバー（.com）
    ↓ example.comサーバーを教える
権威DNSサーバー（example.com）
    ↓ IPアドレスを返す
モバイルアプリ
```

#### プライベートIPとパブリックIP

**プライベートIP**: 内部ネットワークで使用
- 範囲: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`
- 例: `10.0.1.100`（VPC内のECSタスク）
- インターネットから直接アクセス不可

**パブリックIP**: インターネットで使用
- 例: `203.0.113.10`（ALB）
- インターネットから直接アクセス可能

**モバイルBFFでの使い分け**:
```
パブリックIP: ALB
    ↓
プライベートIP: ECSタスク（Ktorアプリ）
    ↓
プライベートIP: RDS（PostgreSQL）
```

#### ポート番号

ポート番号は、1つのIPアドレス上で複数のサービスを区別するための番号。

**ウェルノウンポート（0-1023）**:

| ポート | サービス | 用途 |
|-------|---------|------|
| **80** | HTTP | Webサーバー |
| **443** | HTTPS | 安全なWebサーバー |
| **22** | SSH | リモートログイン |
| **5432** | PostgreSQL | データベース |

**開発でよく使うポート**:

| ポート | 用途 |
|-------|------|
| **8080** | Ktorアプリ（開発） |
| **5432** | PostgreSQL |
| **6379** | Redis |
| **3000** | フロントエンド開発サーバー |

**Ktorでのポート設定**:
```kotlin
// 開発環境
embeddedServer(Netty, port = 8080) {
    // ...
}.start(wait = true)

// application.conf での設定
ktor {
    deployment {
        port = 8080
        port = ${?PORT}  // 環境変数PORTがあればそれを使用
    }
}
```

#### localhost / 127.0.0.1

**localhost**: 自分自身を指す特別なホスト名
**127.0.0.1**: localhostのIPアドレス

```bash
# 開発中のKtorアプリにアクセス
curl http://localhost:8080/api/users
curl http://127.0.0.1:8080/api/users  # 同じ意味
```

**0.0.0.0の意味**:
```kotlin
embeddedServer(Netty, host = "0.0.0.0", port = 8080) {
    // ...
}
```

- `0.0.0.0`: 全てのネットワークインターフェースで受信
- `localhost` / `127.0.0.1`: ローカルからのみアクセス可能
- Docker内のKtorアプリは `0.0.0.0` を使う必要がある

---

### まとめ

この章で学んだこと:

1. ✅ **HTTPリクエスト・レスポンスの構造**
   - リクエストライン、ヘッダー、ボディ
   - ステータスライン、ヘッダー、ボディ

2. ✅ **HTTPメソッドの使い分け**
   - GET: 取得
   - POST: 作成
   - PUT: 更新
   - DELETE: 削除

3. ✅ **HTTPステータスコード**
   - 2xx: 成功
   - 4xx: クライアントエラー
   - 5xx: サーバーエラー

4. ✅ **重要なHTTPヘッダー**
   - Authorization: 認証
   - Content-Type: データ形式
   - CORS: クロスオリジン対応
   - Cookie: セッション管理

5. ✅ **HTTPS/TLS**
   - 暗号化通信
   - ALBでのHTTPS終端

6. ✅ **DNS・IP・ポート**
   - ドメイン名→IPアドレス変換
   - プライベートIP vs パブリックIP
   - ポート番号の役割

---

### 次のステップ

次は**Part 2: Dockerで開発環境を作る**で、実際に開発環境を構築します。

---

### 学習チェックリスト

- [ ] HTTPリクエストの3つの部分を説明できる
- [ ] HTTPメソッドを適切に使い分けられる
- [ ] 主要なHTTPステータスコードを理解している
- [ ] Authorization、Content-Type、CORSヘッダーの役割を理解している
- [ ] HTTPSとHTTPの違いを説明できる
- [ ] DNSがドメイン名をIPアドレスに変換する仕組みを理解している
- [ ] プライベートIPとパブリックIPの違いを理解している
- [ ] ポート番号の役割を理解している
- [ ] KtorでHTTPリクエスト・レスポンスを扱える

全てチェックできたら、次のPartに進みましょう！
