# Part 3: PostgreSQLとExposedの基礎

## Chapter 3: PostgreSQL（Ktorで使う範囲）

### 3.1 データベースの基本概念

#### データベースとは

**データベース**: データを構造化して保存・管理するシステム

**なぜデータベースが必要か**:
- ✅ データの永続化（アプリを再起動してもデータが残る）
- ✅ 効率的な検索（インデックスによる高速検索）
- ✅ データの整合性（トランザクションによる一貫性）
- ✅ 並行アクセス（複数ユーザーの同時アクセス）
- ✅ データの関連付け（リレーションシップ）

#### RDBMSの基本用語

| 用語 | 説明 | 例 |
|-----|------|-----|
| **テーブル** | データを格納する表 | `users`, `orders` |
| **行（レコード）** | 1件のデータ | ユーザー1人分の情報 |
| **列（カラム）** | データの項目 | `id`, `name`, `email` |
| **主キー** | 行を一意に識別する列 | `id` |
| **外部キー** | 他のテーブルを参照する列 | `user_id` |
| **インデックス** | 検索を高速化する仕組み | `email`にインデックス |

**テーブルの例**:

```
users テーブル
+----+----------+-------------------+
| id | name     | email             |
+----+----------+-------------------+
| 1  | Alice    | alice@example.com |
| 2  | Bob      | bob@example.com   |
| 3  | Charlie  | charlie@example.com|
+----+----------+-------------------+
```

#### リレーションシップ

**1対多の関係**: 1人のユーザーが複数の注文を持つ

```
users (1)  ←→  (多) orders
  ↓
1人のユーザー
  ↓
複数の注文
```

**テーブル定義**:

```sql
-- usersテーブル
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL
);

-- ordersテーブル
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### 3.2 PostgreSQLのセットアップ

#### Dockerでの起動

**docker-compose.yml**:

```yaml
version: '3.8'

services:
  db:
    image: postgres:15-alpine
    container_name: ktor-postgres
    environment:
      POSTGRES_DB: ktordb
      POSTGRES_USER: ktoruser
      POSTGRES_PASSWORD: ktorpass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      # 初期化スクリプト
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ktoruser -d ktordb"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

**起動**:
```bash
docker-compose up -d db
```

#### psqlでの接続

```bash
# コンテナに入る
docker-compose exec db psql -U ktoruser -d ktordb

# または、ホストから接続（PostgreSQLクライアントが必要）
psql -h localhost -p 5432 -U ktoruser -d ktordb
```

**基本的なpsqlコマンド**:

```sql
-- データベース一覧
\l

-- テーブル一覧
\dt

-- テーブル構造を表示
\d users

-- SQL実行
SELECT * FROM users;

-- psql終了
\q
```

---

### 3.3 Exposedの基礎

#### Exposedとは

**Exposed**: Kotlin用の軽量なORMライブラリ（JetBrains製）

**特徴**:
- ✅ Kotlin DSLで型安全なSQL
- ✅ コルーチン対応
- ✅ 軽量（JPA/Hibernateより簡単）
- ✅ 2つのAPI: DSL（SQL風）とDAO（オブジェクト風）

**依存関係の追加**:

```kotlin
// build.gradle.kts
dependencies {
    // Exposed
    implementation("org.jetbrains.exposed:exposed-core:0.44.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.44.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.44.1")
    
    // PostgreSQLドライバ
    implementation("org.postgresql:postgresql:42.6.0")
    
    // HikariCP（コネクションプール）
    implementation("com.zaxxer:HikariCP:5.0.1")
}
```

#### テーブル定義（DSL API）

```kotlin
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

// Usersテーブル
object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val createdAt = timestamp("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

// Ordersテーブル
object Orders : Table("orders") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val productName = varchar("product_name", 255)
    val quantity = integer("quantity")
    val createdAt = timestamp("created_at")
    
    override val primaryKey = PrimaryKey(id)
}
```

**主なカラム型**:

| Exposed | PostgreSQL | Kotlin型 |
|---------|-----------|---------|
| `integer()` | INTEGER | Int |
| `long()` | BIGINT | Long |
| `varchar(len)` | VARCHAR(len) | String |
| `text()` | TEXT | String |
| `bool()` | BOOLEAN | Boolean |
| `decimal(p,s)` | DECIMAL(p,s) | BigDecimal |
| `timestamp()` | TIMESTAMP | Instant |

---

### 3.4 データベース接続の設定

#### HikariCPの設定

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

fun initDatabase() {
    val config = HikariConfig().apply {
        jdbcUrl = System.getenv("DB_URL") 
            ?: "jdbc:postgresql://localhost:5432/ktordb"
        driverClassName = "org.postgresql.Driver"
        username = System.getenv("DB_USER") ?: "ktoruser"
        password = System.getenv("DB_PASSWORD") ?: "ktorpass"
        
        // コネクションプール設定
        maximumPoolSize = 10  // 最大接続数
        minimumIdle = 2       // 最小アイドル接続数
        idleTimeout = 600000  // アイドルタイムアウト（10分）
        connectionTimeout = 30000  // 接続タイムアウト（30秒）
        
        // 検証クエリ
        connectionTestQuery = "SELECT 1"
        
        // JDBCプロパティ
        addDataSourceProperty("cachePrepStmts", "true")
        addDataSourceProperty("prepStmtCacheSize", "250")
        addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
    }
    
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)
}
```

#### Application.ktでの初期化

```kotlin
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.module() {
    // データベース接続
    initDatabase()
    
    // テーブル作成（開発環境のみ）
    transaction {
        SchemaUtils.create(Users, Orders)
    }
    
    // プラグイン設定
    configureSerialization()
    configureRouting()
}
```

---

### 3.5 CRUD操作（DSL API）

#### Create - データの挿入

```kotlin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.datetime.Clock

suspend fun createUser(name: String, email: String): Int = 
    transaction {
        Users.insert {
            it[Users.name] = name
            it[Users.email] = email
            it[Users.createdAt] = Clock.System.now()
        } get Users.id
    }

// 使用例
val userId = createUser("Alice", "alice@example.com")
println("Created user with ID: $userId")
```

#### Read - データの取得

**全件取得**:
```kotlin
import org.jetbrains.exposed.sql.selectAll

suspend fun getAllUsers(): List<User> = 
    transaction {
        Users.selectAll()
            .map { row ->
                User(
                    id = row[Users.id],
                    name = row[Users.name],
                    email = row[Users.email],
                    createdAt = row[Users.createdAt]
                )
            }
    }
```

**条件付き取得**:
```kotlin
import org.jetbrains.exposed.sql.select

suspend fun getUserById(id: Int): User? = 
    transaction {
        Users.select { Users.id eq id }
            .map { row ->
                User(
                    id = row[Users.id],
                    name = row[Users.name],
                    email = row[Users.email],
                    createdAt = row[Users.createdAt]
                )
            }
            .singleOrNull()
    }

suspend fun getUserByEmail(email: String): User? = 
    transaction {
        Users.select { Users.email eq email }
            .map { /* ... */ }
            .singleOrNull()
    }
```

**複雑な条件**:
```kotlin
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or

// AND条件
Users.select { 
    (Users.name eq "Alice") and (Users.email like "%@example.com")
}

// OR条件
Users.select { 
    (Users.name eq "Alice") or (Users.name eq "Bob")
}

// IN条件
Users.select { 
    Users.id inList listOf(1, 2, 3)
}
```

#### Update - データの更新

```kotlin
import org.jetbrains.exposed.sql.update

suspend fun updateUserName(id: Int, newName: String): Int = 
    transaction {
        Users.update({ Users.id eq id }) {
            it[name] = newName
        }
    }

// 使用例
val updatedRows = updateUserName(1, "Alice Smith")
if (updatedRows > 0) {
    println("User updated")
}
```

#### Delete - データの削除

```kotlin
import org.jetbrains.exposed.sql.deleteWhere

suspend fun deleteUser(id: Int): Int = 
    transaction {
        Users.deleteWhere { Users.id eq id }
    }

// 使用例
val deletedRows = deleteUser(1)
if (deletedRows > 0) {
    println("User deleted")
}
```

---

### 3.6 JOIN操作

#### INNER JOIN

```kotlin
import org.jetbrains.exposed.sql.innerJoin

data class UserWithOrders(
    val userId: Int,
    val userName: String,
    val orderId: Int,
    val productName: String,
    val quantity: Int
)

suspend fun getUsersWithOrders(): List<UserWithOrders> = 
    transaction {
        Users.innerJoin(Orders, { Users.id }, { Orders.userId })
            .selectAll()
            .map { row ->
                UserWithOrders(
                    userId = row[Users.id],
                    userName = row[Users.name],
                    orderId = row[Orders.id],
                    productName = row[Orders.productName],
                    quantity = row[Orders.quantity]
                )
            }
    }
```

#### LEFT JOIN

```kotlin
import org.jetbrains.exposed.sql.leftJoin

// 注文がないユーザーも含める
suspend fun getAllUsersWithOptionalOrders() = 
    transaction {
        Users.leftJoin(Orders, { Users.id }, { Orders.userId })
            .selectAll()
            .map { row ->
                UserWithOrders(
                    userId = row[Users.id],
                    userName = row[Users.name],
                    orderId = row[Orders.id].toInt(),  // nullの可能性
                    productName = row[Orders.productName] ?: "",
                    quantity = row[Orders.quantity] ?: 0
                )
            }
    }
```

---

### 3.7 トランザクション

#### トランザクションとは

**トランザクション**: 複数の操作をまとめて実行し、全て成功するか全て失敗するかを保証

**ACID特性**:
- **Atomicity（原子性）**: 全て成功か全て失敗
- **Consistency（一貫性）**: データの整合性を保つ
- **Isolation（分離性）**: 並行実行しても影響しない
- **Durability（永続性）**: 確定したら永続化

#### トランザクションの使用

```kotlin
import org.jetbrains.exposed.sql.transactions.transaction

suspend fun transferOrder(fromUserId: Int, toUserId: Int, orderId: Int) {
    transaction {
        // 1. 注文の存在確認
        val order = Orders.select { Orders.id eq orderId }
            .singleOrNull()
            ?: throw NotFoundException("Order not found")
        
        // 2. 所有者確認
        if (order[Orders.userId] != fromUserId) {
            throw ForbiddenException("Not your order")
        }
        
        // 3. 注文の移転
        Orders.update({ Orders.id eq orderId }) {
            it[userId] = toUserId
        }
        
        // エラーが発生すれば自動的にロールバック
        // 正常終了すればコミット
    }
}
```

#### 明示的なコミット・ロールバック

```kotlin
transaction {
    try {
        // 複数の操作
        Users.insert { /* ... */ }
        Orders.insert { /* ... */ }
        
        commit()  // 明示的なコミット
    } catch (e: Exception) {
        rollback()  // 明示的なロールバック
        throw e
    }
}
```

---

### 3.8 Repositoryパターン

#### なぜRepositoryパターンを使うのか

**メリット**:
- ✅ ビジネスロジックとデータアクセスを分離
- ✅ テストが容易（モックに置き換え可能）
- ✅ データベースの変更が容易

#### インターフェースの定義

```kotlin
interface UserRepository {
    suspend fun findById(id: Int): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findAll(): List<User>
    suspend fun create(name: String, email: String): User
    suspend fun update(id: Int, name: String, email: String): User?
    suspend fun delete(id: Int): Boolean
}
```

#### 実装クラス

```kotlin
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepositoryImpl : UserRepository {
    override suspend fun findById(id: Int): User? = 
        transaction {
            Users.select { Users.id eq id }
                .map { it.toUser() }
                .singleOrNull()
        }
    
    override suspend fun findByEmail(email: String): User? = 
        transaction {
            Users.select { Users.email eq email }
                .map { it.toUser() }
                .singleOrNull()
        }
    
    override suspend fun findAll(): List<User> = 
        transaction {
            Users.selectAll()
                .map { it.toUser() }
        }
    
    override suspend fun create(name: String, email: String): User = 
        transaction {
            val id = Users.insert {
                it[Users.name] = name
                it[Users.email] = email
                it[Users.createdAt] = Clock.System.now()
            } get Users.id
            
            User(id, name, email, Clock.System.now())
        }
    
    override suspend fun update(id: Int, name: String, email: String): User? = 
        transaction {
            val updated = Users.update({ Users.id eq id }) {
                it[Users.name] = name
                it[Users.email] = email
            }
            
            if (updated > 0) findById(id) else null
        }
    
    override suspend fun delete(id: Int): Boolean = 
        transaction {
            Users.deleteWhere { Users.id eq id } > 0
        }
    
    // ヘルパー関数
    private fun ResultRow.toUser() = User(
        id = this[Users.id],
        name = this[Users.name],
        email = this[Users.email],
        createdAt = this[Users.createdAt]
    )
}
```

#### Service層での使用

```kotlin
class UserService(
    private val userRepository: UserRepository
) {
    suspend fun getUser(id: Int): User {
        return userRepository.findById(id)
            ?: throw NotFoundException("User not found")
    }
    
    suspend fun createUser(name: String, email: String): User {
        // バリデーション
        if (name.isBlank()) {
            throw ValidationException("Name is required")
        }
        
        // 重複チェック
        val existing = userRepository.findByEmail(email)
        if (existing != null) {
            throw ConflictException("Email already exists")
        }
        
        // 作成
        return userRepository.create(name, email)
    }
}
```

#### Ktorでの依存性注入

```kotlin
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val userRepository: UserRepository = UserRepositoryImpl()
    val userService = UserService(userRepository)
    
    routing {
        route("/api/users") {
            get {
                val users = userService.getAllUsers()
                call.respond(users)
            }
            
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        "Invalid ID"
                    )
                
                val user = userService.getUser(id)
                call.respond(user)
            }
            
            post {
                val request = call.receive<CreateUserRequest>()
                val user = userService.createUser(
                    request.name,
                    request.email
                )
                call.respond(HttpStatusCode.Created, user)
            }
        }
    }
}
```

---

### 3.9 マイグレーション（Flyway）

#### なぜマイグレーションが必要か

**問題**:
- スキーマ変更の管理が困難
- 本番環境への適用ミス
- バージョン管理ができない

**解決策**: Flywayでマイグレーションを管理

#### Flywayのセットアップ

**依存関係の追加**:
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.flywaydb:flyway-core:9.22.0")
    implementation("org.flywaydb:flyway-database-postgresql:9.22.0")
}
```

**マイグレーションスクリプト**:

```
src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__create_orders_table.sql
└── V3__add_user_phone_column.sql
```

**V1__create_users_table.sql**:
```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
```

**V2__create_orders_table.sql**:
```sql
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
```

#### Flywayの実行

```kotlin
import org.flywaydb.core.Flyway

fun runMigrations() {
    val flyway = Flyway.configure()
        .dataSource(
            System.getenv("DB_URL"),
            System.getenv("DB_USER"),
            System.getenv("DB_PASSWORD")
        )
        .locations("classpath:db/migration")
        .load()
    
    flyway.migrate()
}

// Application.ktで実行
fun Application.module() {
    runMigrations()  // マイグレーション実行
    initDatabase()   // データベース接続
    // ...
}
```

---

### まとめ

この章で学んだこと:

1. ✅ **PostgreSQLの基礎**
   - テーブル、行、列
   - リレーションシップ
   - Dockerでのセットアップ

2. ✅ **Exposed ORM**
   - テーブル定義
   - CRUD操作
   - JOIN

3. ✅ **トランザクション**
   - ACID特性
   - コミット・ロールバック

4. ✅ **Repositoryパターン**
   - データアクセスの分離
   - テスタビリティ

5. ✅ **マイグレーション**
   - Flywayの使用
   - スキーマ変更の管理

---

### 次のステップ

次は**Part 4: モバイルBFFの認証パターン**で、OAuth 2.0とJWT認証を学びます。

---

### 学習チェックリスト

- [ ] データベースの基本用語を理解している
- [ ] PostgreSQLをDockerで起動できる
- [ ] Exposedでテーブルを定義できる
- [ ] CRUD操作を実装できる
- [ ] JOINを使ってデータを取得できる
- [ ] トランザクションを使用できる
- [ ] Repositoryパターンを実装できる
- [ ] Flywayでマイグレーションを管理できる

全てチェックできたら、次のPartに進みましょう！
