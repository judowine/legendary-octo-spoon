# Part 2: Dockerで開発環境を作る

## Chapter 2: Docker基礎（Ktor開発に必要な範囲）

### 2.1 なぜDockerを使うのか

#### 従来の開発環境の問題

**「自分のマシンでは動くのに...」問題**:

```
開発者A: PostgreSQL 14, Java 17
開発者B: PostgreSQL 15, Java 11
本番環境: PostgreSQL 15, Java 17

→ 環境の違いでバグが発生
```

**セットアップの複雑さ**:
```
1. PostgreSQLをインストール
2. Redisをインストール
3. 設定ファイルを編集
4. サービスを起動
5. データベースを作成
6. ...（多数の手順）
```

#### Dockerが解決する問題

**環境の再現性**:
```yaml
# docker-compose.yml（誰でも同じ環境）
services:
  db:
    image: postgres:15
  redis:
    image: redis:7
  app:
    build: .
```

```bash
# 1コマンドで全て起動
docker-compose up
```

**メリット**:
- ✅ **環境の統一**: 開発、ステージング、本番で同じ
- ✅ **セットアップが簡単**: `docker-compose up`だけ
- ✅ **クリーンな環境**: ホストマシンを汚さない
- ✅ **依存関係の分離**: プロジェクトごとに独立
- ✅ **素早いスタート**: 新メンバーがすぐ開始できる
- ✅ **本番と同じ**: 本番環境もDockerを使用

---

### 2.2 Dockerの基本概念

#### イメージとコンテナ

**イメージ**: アプリケーションとその依存関係をパッケージしたテンプレート
- 例: `postgres:15`, `eclipse-temurin:17-jre-alpine`
- 読み取り専用
- レシピのようなもの

**コンテナ**: イメージから作成された実行環境
- 例: 実際に動いているPostgreSQLサーバー
- 読み書き可能
- レシピから作った料理のようなもの

```
イメージ（レシピ）
    ↓ docker run
コンテナ（実行中のアプリ）
```

#### Dockerの仕組み

```
ホストOS（macOS, Windows, Linux）
    ↓
Docker Engine
    ↓
┌──────────────┬──────────────┬──────────────┐
│ コンテナ1    │ コンテナ2    │ コンテナ3    │
│ (Ktorアプリ) │ (PostgreSQL) │ (Redis)      │
└──────────────┴──────────────┴──────────────┘
```

**重要な特徴**:
- コンテナは互いに分離されている
- 軽量（仮想マシンより高速）
- ホストOSのカーネルを共有

---

### 2.3 Ktorアプリのコンテナ化

#### Dockerfileの作成

Dockerfileは、イメージの作り方を記述したファイルです。

**基本的なDockerfile**:

```dockerfile
# ベースイメージ
FROM eclipse-temurin:17-jre-alpine

# 作業ディレクトリ
WORKDIR /app

# Fat JARをコピー
COPY build/libs/app-all.jar /app/app.jar

# ポートを公開
EXPOSE 8080

# アプリケーションを起動
CMD ["java", "-jar", "/app/app.jar"]
```

**問題点**: Fat JARを事前にビルドする必要がある

#### マルチステージビルド（推奨）

**マルチステージビルド**: 1つのDockerfileで、ビルドと実行を分離

```dockerfile
# ========== ビルドステージ ==========
FROM gradle:8.5-jdk17 AS build

# ソースコードをコピー
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

# Fat JARをビルド
RUN gradle buildFatJar --no-daemon

# ========== 実行ステージ ==========
FROM eclipse-temurin:17-jre-alpine

# 非rootユーザーを作成
RUN addgroup -g 1000 ktor && \
    adduser -D -u 1000 -G ktor ktor

WORKDIR /app

# ビルドステージからFat JARをコピー
COPY --from=build --chown=ktor:ktor \
     /home/gradle/src/build/libs/*-all.jar /app/app.jar

# ヘルスチェック
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -q --spider http://localhost:8080/health || exit 1

# 非rootユーザーで実行
USER ktor

# ポートを公開
EXPOSE 8080

# JVMオプション
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# 起動コマンド
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

**メリット**:
- ✅ **小さなイメージ**: 実行に必要なものだけ（約120-150MB）
- ✅ **セキュリティ**: 非rootユーザーで実行
- ✅ **ヘルスチェック**: コンテナの健全性を監視
- ✅ **最適化されたJVM**: コンテナ対応の設定

#### Dockerイメージのビルド

```bash
# イメージをビルド
docker build -t ktor-bff:latest .

# ビルド過程の確認
docker build -t ktor-bff:latest . --progress=plain

# イメージの確認
docker images
# REPOSITORY   TAG       IMAGE ID       CREATED         SIZE
# ktor-bff     latest    abc123def456   5 seconds ago   145MB
```

#### コンテナの実行

```bash
# コンテナを起動
docker run -p 8080:8080 ktor-bff:latest

# バックグラウンドで起動
docker run -d -p 8080:8080 --name ktor-app ktor-bff:latest

# 環境変数を渡す
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://db:5432/mydb \
  -e DB_USER=myuser \
  -e DB_PASSWORD=mypassword \
  ktor-bff:latest
```

**ポートマッピング** (`-p`):
```
-p ホストポート:コンテナポート
-p 8080:8080
```

- ホストの8080番ポート → コンテナの8080番ポート
- `http://localhost:8080`でアクセス可能

---

### 2.4 docker-composeで開発環境を構築

#### docker-composeとは

**docker-compose**: 複数のコンテナを定義・管理するツール

**メリット**:
- 複数のサービスを1つのファイルで定義
- 1コマンドで全て起動・停止
- ネットワークを自動で構築
- 環境変数の管理が簡単

#### 基本的なdocker-compose.yml

```yaml
version: '3.8'

services:
  # PostgreSQLデータベース
  db:
    image: postgres:15-alpine
    container_name: ktor-db
    environment:
      POSTGRES_DB: mydb
      POSTGRES_USER: myuser
      POSTGRES_PASSWORD: mypassword
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U myuser -d mydb"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis
  redis:
    image: redis:7-alpine
    container_name: ktor-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Ktorアプリケーション
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: ktor-app
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:postgresql://db:5432/mydb
      DB_USER: myuser
      DB_PASSWORD: mypassword
      REDIS_HOST: redis
      REDIS_PORT: 6379
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy

# 永続化ボリューム
volumes:
  postgres_data:
```

#### 環境変数の管理（.env）

`.env`ファイルで環境変数を管理:

```bash
# .env
POSTGRES_DB=mydb
POSTGRES_USER=myuser
POSTGRES_PASSWORD=mypassword

DB_URL=jdbc:postgresql://db:5432/mydb
DB_USER=myuser
DB_PASSWORD=mypassword

REDIS_HOST=redis
REDIS_PORT=6379
```

**docker-compose.yml**を更新:
```yaml
services:
  db:
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    # ...

  app:
    environment:
      DB_URL: ${DB_URL}
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_HOST: ${REDIS_HOST}
    # ...
```

**重要**: `.env`ファイルは`.gitignore`に追加

```bash
# .gitignore
.env
```

#### docker-composeコマンド

**起動**:
```bash
# 全サービスを起動（フォアグラウンド）
docker-compose up

# バックグラウンドで起動
docker-compose up -d

# 特定のサービスのみ起動
docker-compose up db redis

# ビルドしてから起動
docker-compose up --build
```

**停止**:
```bash
# 停止（コンテナは残る）
docker-compose stop

# 停止して削除
docker-compose down

# ボリュームも削除
docker-compose down -v
```

**ログ確認**:
```bash
# 全サービスのログ
docker-compose logs

# 特定のサービス
docker-compose logs app

# リアルタイムで表示
docker-compose logs -f app

# 最新100行
docker-compose logs --tail=100 app
```

**サービスの状態確認**:
```bash
# 実行中のサービス一覧
docker-compose ps

# 詳細情報
docker-compose ps -a
```

**コンテナ内でコマンド実行**:
```bash
# アプリコンテナでシェルを起動
docker-compose exec app sh

# データベースに接続
docker-compose exec db psql -U myuser -d mydb

# Redisに接続
docker-compose exec redis redis-cli
```

#### 開発用の設定

**ホットリロード対応**:

```yaml
services:
  app:
    build:
      context: .
      dockerfile: Dockerfile.dev  # 開発用Dockerfile
    volumes:
      # ソースコードをマウント
      - ./src:/app/src
      - ./build.gradle.kts:/app/build.gradle.kts
    environment:
      # 開発モード
      KTOR_ENV: development
    command: ./gradlew run --continuous
```

**Dockerfile.dev**:
```dockerfile
FROM gradle:8.5-jdk17

WORKDIR /app

# Gradleの依存関係をキャッシュ
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon

# ソースコードをコピー
COPY . .

# 開発サーバーを起動
CMD ["gradle", "run", "--continuous"]
```

---

### 2.5 よく使うDockerコマンド

#### イメージ関連

```bash
# イメージ一覧
docker images

# イメージを削除
docker rmi ktor-bff:latest

# 未使用イメージを削除
docker image prune

# 全ての未使用イメージを削除
docker image prune -a
```

#### コンテナ関連

```bash
# 実行中のコンテナ一覧
docker ps

# 全てのコンテナ一覧（停止中も含む）
docker ps -a

# コンテナを停止
docker stop ktor-app

# コンテナを削除
docker rm ktor-app

# 実行中のコンテナを強制削除
docker rm -f ktor-app

# 停止中のコンテナを全て削除
docker container prune
```

#### ログとデバッグ

```bash
# ログを表示
docker logs ktor-app

# ログをリアルタイムで表示
docker logs -f ktor-app

# コンテナ内でコマンド実行
docker exec -it ktor-app sh

# コンテナの詳細情報
docker inspect ktor-app

# リソース使用状況
docker stats ktor-app
```

#### ネットワーク関連

```bash
# ネットワーク一覧
docker network ls

# ネットワークの詳細
docker network inspect ktor_default

# コンテナのIPアドレス確認
docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ktor-app
```

---

### 2.6 トラブルシューティング

#### コンテナが起動しない

**症状**: `docker-compose up`でエラー

**原因と対処法**:

1. **ポートが既に使用されている**
```bash
# エラー例
Error starting userland proxy: listen tcp 0.0.0.0:8080: bind: address already in use

# 解決法: ポートを使用しているプロセスを確認
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# または、docker-compose.ymlのポートを変更
ports:
  - "8081:8080"  # ホスト側を8081に変更
```

2. **イメージのビルドエラー**
```bash
# キャッシュをクリアして再ビルド
docker-compose build --no-cache
```

3. **依存サービスが起動していない**
```yaml
# healthcheckを追加
services:
  db:
    healthcheck:
      test: ["CMD-SHELL", "pg_isready"]
      interval: 10s
  
  app:
    depends_on:
      db:
        condition: service_healthy
```

#### データベースに接続できない

**症状**: アプリがデータベースに接続できない

**原因と対処法**:

1. **ホスト名が間違っている**
```kotlin
// ❌ 間違い
val dbUrl = "jdbc:postgresql://localhost:5432/mydb"

// ✅ 正しい（docker-compose内では、サービス名を使用）
val dbUrl = "jdbc:postgresql://db:5432/mydb"
```

2. **データベースが起動していない**
```bash
# ログを確認
docker-compose logs db

# データベースに直接接続して確認
docker-compose exec db psql -U myuser -d mydb
```

3. **ネットワークの問題**
```bash
# コンテナが同じネットワークにいるか確認
docker network inspect ktor_default
```

#### コンテナのディスク使用量が増える

```bash
# ディスク使用量を確認
docker system df

# 未使用のリソースを削除
docker system prune

# ボリュームも含めて削除
docker system prune -a --volumes
```

#### コンテナ内でデバッグ

```bash
# コンテナ内に入る
docker-compose exec app sh

# 環境変数を確認
env

# ネットワーク接続を確認
ping db
nc -zv db 5432  # PostgreSQLポートが開いているか

# プロセスを確認
ps aux

# ログファイルを確認
ls -la /app/logs/
```

---

### 2.7 本番環境への準備

#### .dockerignoreの作成

不要なファイルをイメージに含めないようにします。

```.dockerignore
# Build artifacts
build/
.gradle/
*.jar
*.log

# IDE
.idea/
.vscode/
*.iml

# Git
.git/
.gitignore

# Docker
Dockerfile
docker-compose.yml
.dockerignore

# Env files
.env
.env.local

# Tests
src/test/

# Documentation
README.md
docs/
```

#### マルチステージビルドの最適化

```dockerfile
# ========== ビルドステージ ==========
FROM gradle:8.5-jdk17 AS build

# Gradleの依存関係を先にキャッシュ
COPY build.gradle.kts settings.gradle.kts /home/gradle/src/
WORKDIR /home/gradle/src
RUN gradle dependencies --no-daemon

# ソースコードをコピー
COPY --chown=gradle:gradle . /home/gradle/src

# Fat JARをビルド
RUN gradle buildFatJar --no-daemon

# ========== 実行ステージ ==========
FROM eclipse-temurin:17-jre-alpine

# 必要最小限のパッケージをインストール
RUN apk add --no-cache wget

# 非rootユーザーを作成
RUN addgroup -g 1000 ktor && \
    adduser -D -u 1000 -G ktor ktor

WORKDIR /app

# Fat JARをコピー
COPY --from=build --chown=ktor:ktor \
     /home/gradle/src/build/libs/*-all.jar /app/app.jar

# ヘルスチェック
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -q --spider http://localhost:8080/health || exit 1

USER ktor
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

#### セキュリティのベストプラクティス

1. **非rootユーザーで実行**
```dockerfile
USER ktor  # rootではなく一般ユーザー
```

2. **最小限のベースイメージ**
```dockerfile
FROM eclipse-temurin:17-jre-alpine  # alpineは軽量
```

3. **機密情報を含めない**
```dockerfile
# ❌ 絶対にやらない
ENV DB_PASSWORD=secret123

# ✅ 実行時に環境変数で渡す
docker run -e DB_PASSWORD=${DB_PASSWORD} ktor-bff
```

4. **レイヤーを最適化**
```dockerfile
# ❌ 非効率（毎回全て再実行）
COPY . /app
RUN gradle build

# ✅ 効率的（依存関係をキャッシュ）
COPY build.gradle.kts /app/
RUN gradle dependencies
COPY . /app
RUN gradle build
```

---

### まとめ

この章で学んだこと:

1. ✅ **Dockerの必要性**
   - 環境の再現性
   - セットアップの簡単化

2. ✅ **Dockerの基本概念**
   - イメージとコンテナ
   - Dockerfile

3. ✅ **Ktorアプリのコンテナ化**
   - マルチステージビルド
   - ヘルスチェック
   - セキュリティ

4. ✅ **docker-compose**
   - 複数サービスの管理
   - 環境変数の管理
   - よく使うコマンド

5. ✅ **トラブルシューティング**
   - よくあるエラーと対処法
   - デバッグ方法

6. ✅ **本番環境への準備**
   - .dockerignore
   - 最適化
   - セキュリティ

---

### 次のステップ

次は**Part 3: PostgreSQLとExposedの基礎**で、データベースの統合を学びます。

---

### 学習チェックリスト

- [ ] Dockerが解決する問題を説明できる
- [ ] イメージとコンテナの違いを理解している
- [ ] Dockerfileを書ける
- [ ] マルチステージビルドの利点を理解している
- [ ] docker-compose.ymlを書ける
- [ ] docker-composeコマンドを使える
- [ ] よくあるエラーに対処できる
- [ ] .dockerignoreの役割を理解している
- [ ] セキュリティのベストプラクティスを理解している

全てチェックできたら、次のPartに進みましょう！
