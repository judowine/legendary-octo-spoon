# ビルドステージ
FROM gradle:8-jdk17 AS build
WORKDIR /app

# Gradleキャッシュを活用するため、依存関係定義を先にコピー
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

# 依存関係をダウンロード（キャッシュ層として機能）
RUN gradle dependencies --no-daemon || true

# ソースコードをコピー
COPY src ./src

# アプリケーションをビルド
RUN gradle build --no-daemon -x test

# 実行ステージ
FROM eclipse-temurin:17-jre-alpine

# 必要なパッケージをインストール（ヘルスチェック用）
RUN apk add --no-cache wget

WORKDIR /app

# ビルドステージからJARファイルをコピー
COPY --from=build /app/build/libs/*.jar app.jar

# ポート公開
EXPOSE 8080

# アプリケーション実行
ENTRYPOINT ["java", "-jar", "app.jar"]
