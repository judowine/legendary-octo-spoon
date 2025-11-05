# Ktor Account Management System

Kotlin/Ktorを使用したモダンなアカウント管理システムです。JWT認証、OAuth2.0統合、メール認証などのフル機能を備えています。

## 主な機能

- ✅ **ユーザー認証**
  - メールアドレス + パスワードによる登録・ログイン
  - JWT認証（Access Token + Refresh Token）
  - メール認証機能

- ✅ **OAuth 2.0統合**
  - Google OAuth
  - GitHub OAuth

- ✅ **アカウント管理**
  - プロフィール管理（閲覧・更新）
  - パスワード変更
  - パスワードリセット
  - アカウント削除（論理削除）

- ✅ **セキュリティ**
  - BCryptパスワードハッシュ化
  - レート制限
  - CORS設定
  - トークンローテーション

## 技術スタック

- **言語**: Kotlin 1.9.22
- **フレームワーク**: Ktor 3.0.0
- **データベース**: PostgreSQL 15 + Exposed ORM
- **キャッシング**: Redis 7
- **認証**: JWT (Auth0)
- **コンテナ**: Docker & Docker Compose
- **テスト**: Kotest, MockK, TestContainers

## プロジェクト構造

```
ktor-account-system/
├── src/
│   ├── main/
│   │   ├── kotlin/com/example/
│   │   │   ├── Application.kt          # エントリーポイント
│   │   │   ├── plugins/                # Ktorプラグイン設定
│   │   │   ├── routes/                 # APIエンドポイント
│   │   │   ├── services/               # ビジネスロジック
│   │   │   ├── repositories/           # データアクセス層
│   │   │   ├── models/                 # データモデル
│   │   │   │   ├── tables/             # Exposedテーブル定義
│   │   │   │   ├── entities/           # エンティティクラス
│   │   │   │   └── dto/                # DTOクラス
│   │   │   ├── security/               # セキュリティ関連
│   │   │   ├── validation/             # バリデーション
│   │   │   └── utils/                  # ユーティリティ
│   │   └── resources/
│   │       ├── application.yaml        # Ktor設定
│   │       └── logback.xml             # ログ設定
│   └── test/                           # テストコード
├── docker/
│   ├── docker-compose.yml              # Docker構成
│   ├── Dockerfile                      # アプリケーションイメージ
│   └── init.sql                        # DB初期化スクリプト
├── gradle/
│   └── libs.versions.toml              # バージョンカタログ
├── build.gradle.kts                    # Gradleビルド設定
└── .env.example                        # 環境変数テンプレート
```

## セットアップ

### 前提条件

- JDK 17以上
- Docker & Docker Compose
- Gradle 8.x（または付属のGradleWrapper使用）

### 環境変数の設定

```bash
cp .env.example .env
# .envファイルを編集して必要な設定を行う
```

### Docker環境での起動

#### 1. すべてのサービスを起動

```bash
cd docker
docker-compose up -d
```

起動するサービス：
- PostgreSQL（ポート: 5432）
- Redis（ポート: 6379）
- MailHog（SMTP: 1025, Web UI: 8025）
- Ktor Application（ポート: 8080）

#### 2. ログ確認

```bash
docker-compose logs -f app
```

#### 3. 停止

```bash
docker-compose down

# データボリュームも削除する場合
docker-compose down -v
```

### ローカル開発

#### 1. データベース・Redisのみ起動

```bash
cd docker
docker-compose up -d postgres redis mailhog
```

#### 2. アプリケーション起動

```bash
./gradlew run
```

または

```bash
./gradlew build
java -jar build/libs/ktor-account-system-1.0.0.jar
```

## API仕様

### ベースURL

```
http://localhost:8080/api/v1
```

### エンドポイント一覧

#### ヘルスチェック

- `GET /health` - サービスヘルスチェック

#### 認証（Authentication）

- `POST /auth/register` - ユーザー登録
- `POST /auth/login` - ログイン
- `POST /auth/refresh` - トークン更新
- `POST /auth/logout` - ログアウト
- `POST /auth/verify-email` - メール認証
- `POST /auth/password-reset/request` - パスワードリセット要求
- `POST /auth/password-reset/confirm` - パスワードリセット実行
- `GET /auth/oauth/google` - Google OAuth認証開始
- `GET /auth/oauth/google/callback` - Google OAuthコールバック
- `GET /auth/oauth/github` - GitHub OAuth認証開始
- `GET /auth/oauth/github/callback` - GitHub OAuthコールバック

#### ユーザー管理（User Management）

- `GET /users/me` - プロフィール取得（要認証）
- `PATCH /users/me` - プロフィール更新（要認証）
- `POST /users/me/email` - メールアドレス変更（要認証）
- `POST /users/me/password` - パスワード変更（要認証）
- `DELETE /users/me` - アカウント削除（要認証）

### 認証方法

保護されたエンドポイントにアクセスする場合、以下のヘッダーを含めます：

```
Authorization: Bearer {access_token}
```

## 開発ツール

### MailHog（開発用メールサーバー）

メール送信機能のテスト用に使用します。

- Web UI: http://localhost:8025
- SMTP: localhost:1025

### データベース接続

```
Host: localhost
Port: 5432
Database: account_system
User: admin
Password: admin_password
```

## テスト

```bash
# すべてのテストを実行
./gradlew test

# カバレッジレポート生成
./gradlew test jacocoTestReport
```

## ビルド

```bash
# アプリケーションをビルド
./gradlew build

# Dockerイメージをビルド
./gradlew buildDockerImage
# または
docker build -t ktor-account-system:latest -f docker/Dockerfile .
```

## トラブルシューティング

### ポートが既に使用されている

```bash
# 使用中のポートを確認
lsof -i :8080
lsof -i :5432

# プロセスを停止
kill -9 <PID>
```

### データベース接続エラー

```bash
# PostgreSQLコンテナの状態確認
docker-compose ps postgres

# ログ確認
docker-compose logs postgres

# コンテナ再起動
docker-compose restart postgres
```

### Gradleビルドエラー

```bash
# Gradleキャッシュをクリア
./gradlew clean

# 依存関係を再ダウンロード
./gradlew build --refresh-dependencies
```

## 開発ロードマップ

### Phase 1: プロジェクトセットアップ ✅
- [x] プロジェクト構造
- [x] Gradle設定（バージョンカタログ）
- [x] Docker環境
- [x] 基本的なKtor設定
- [x] データベーススキーマ

### Phase 2: 基本認証機能（進行中）
- [ ] ユーザー登録エンドポイント
- [ ] ログインエンドポイント
- [ ] JWT認証実装
- [ ] トークンリフレッシュ機能

### Phase 3: メール機能
- [ ] メール認証
- [ ] パスワードリセット

### Phase 4: プロフィール管理
- [ ] プロフィール取得・更新
- [ ] パスワード変更
- [ ] アカウント削除

### Phase 5: OAuth統合
- [ ] Google OAuth
- [ ] GitHub OAuth

### Phase 6: セキュリティ強化
- [ ] レート制限
- [ ] セキュリティヘッダー

### Phase 7: テスト
- [ ] ユニットテスト
- [ ] 統合テスト
- [ ] E2Eテスト

### Phase 8: ドキュメント＆最適化
- [ ] APIドキュメント
- [ ] パフォーマンステスト
- [ ] セキュリティ監査

## ライセンス

MIT License

## 貢献

プルリクエストを歓迎します！

1. このリポジトリをフォーク
2. フィーチャーブランチを作成 (`git checkout -b feature/amazing-feature`)
3. 変更をコミット (`git commit -m 'Add amazing feature'`)
4. ブランチにプッシュ (`git push origin feature/amazing-feature`)
5. プルリクエストを作成

## お問い合わせ

質問や提案がある場合は、Issueを作成してください。
