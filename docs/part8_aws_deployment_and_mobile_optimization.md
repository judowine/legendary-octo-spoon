# Part 8: AWSデプロイとモバイル最適化

## Chapter 8: AWS ECS/Fargateへのデプロイ

### 8.1 AWSサービスの全体像

#### モバイルBFF用のAWS構成

```
Internet
  ↓ HTTPS
Route 53 (DNS)
  ↓
CloudFront (CDN) ※オプション
  ↓
ALB (Application Load Balancer) - HTTPS終端
  ↓ HTTP
ECS/Fargate (Ktorアプリ)
  ↓
┌─────────────┬──────────────┬──────────────┐
│   RDS       │  ElastiCache │   Secrets    │
│ (PostgreSQL)│   (Redis)    │   Manager    │
└─────────────┴──────────────┴──────────────┘
```

#### 使用するAWSサービス

| サービス | 用途 | コスト目安 |
|---------|------|----------|
| **ECS/Fargate** | コンテナ実行 | $35-700/月 |
| **ALB** | ロードバランサー | $16/月 + データ転送 |
| **ECR** | コンテナレジストリ | $0.10/GB/月 |
| **RDS** | PostgreSQL | $15-200/月 |
| **ElastiCache** | Redis | $15-100/月 |
| **CloudWatch** | ログ・メトリクス | $5-50/月 |
| **Secrets Manager** | 秘密情報管理 | $0.40/secret/月 |
| **VPC** | ネットワーク | 無料 |
| **Route 53** | DNS | $0.50/ホストゾーン/月 |

**小規模BFF（開発環境）**: 約$100-150/月
**中規模BFF（本番環境）**: 約$300-500/月
**大規模BFF（高トラフィック）**: $1,000+/月

---

### 8.2 IAM（権限管理）

#### タスクロール vs 実行ロール

**タスクロール**: コンテナ内のアプリケーションがAWSサービスにアクセスするための権限
**実行ロール**: ECSがコンテナを起動・管理するための権限

```
┌─────────────────┐
│ ECS Task        │
│  ┌───────────┐  │
│  │ Ktorアプリ │  │ ← タスクロール（S3, DynamoDB等にアクセス）
│  └───────────┘  │
└─────────────────┘
        ↑
    実行ロール（ECRからイメージ取得、CloudWatchにログ送信）
```

#### タスクロールの作成

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": "arn:aws:s3:::my-bucket/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:ap-northeast-1:123456789012:secret:ktor-bff/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:Query"
      ],
      "Resource": "arn:aws:dynamodb:ap-northeast-1:123456789012:table/my-table"
    }
  ]
}
```

#### 実行ロールの作成

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:ap-northeast-1:123456789012:log-group:/ecs/ktor-bff:*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:ap-northeast-1:123456789012:secret:ktor-bff/*"
    }
  ]
}
```

---

### 8.3 VPC構成

#### VPCの設計

```
VPC (10.0.0.0/16)
├── Public Subnet A (10.0.1.0/24) - ap-northeast-1a
│   └── ALB
├── Public Subnet B (10.0.2.0/24) - ap-northeast-1c
│   └── ALB
├── Private Subnet A (10.0.11.0/24) - ap-northeast-1a
│   └── ECS Tasks
├── Private Subnet B (10.0.12.0/24) - ap-northeast-1c
│   └── ECS Tasks
├── Data Subnet A (10.0.21.0/24) - ap-northeast-1a
│   └── RDS Primary
└── Data Subnet B (10.0.22.0/24) - ap-northeast-1c
    └── RDS Standby
```

**なぜ分けるのか**:
- **Public Subnet**: インターネットからのアクセスを受ける（ALB）
- **Private Subnet**: 外部から直接アクセスできない（ECS、安全）
- **Data Subnet**: データベース専用（最も保護される）

#### セキュリティグループ

**ALB セキュリティグループ**:
```
Inbound:
- Port 443 (HTTPS) from 0.0.0.0/0 (インターネット全体)
- Port 80 (HTTP) from 0.0.0.0/0 (HTTPSへリダイレクト用)

Outbound:
- Port 8080 to ECS セキュリティグループ
```

**ECS セキュリティグループ**:
```
Inbound:
- Port 8080 from ALB セキュリティグループ

Outbound:
- Port 5432 to RDS セキュリティグループ
- Port 6379 to ElastiCache セキュリティグループ
- Port 443 to 0.0.0.0/0 (外部APIアクセス)
```

**RDS セキュリティグループ**:
```
Inbound:
- Port 5432 from ECS セキュリティグループ

Outbound:
- なし
```

#### VPC Endpoints

NAT Gatewayの代わりにVPC Endpointsを使用してコストを削減：

```
VPC Endpoints（推奨）:
- com.amazonaws.ap-northeast-1.ecr.dkr ($7/月)
- com.amazonaws.ap-northeast-1.ecr.api ($7/月)
- com.amazonaws.ap-northeast-1.s3 (Gateway型、無料)
- com.amazonaws.ap-northeast-1.logs ($7/月)
- com.amazonaws.ap-northeast-1.secretsmanager ($7/月)

合計: 約$28/月

NAT Gateway:
- $32/月 + データ転送料
```

**コスト削減**: VPC Endpoints使用で月額$4+データ転送料を節約

---

### 8.4 ECR（コンテナレジストリ）

#### ECRリポジトリの作成

```bash
# AWS CLIでリポジトリを作成
aws ecr create-repository \
  --repository-name ktor-bff \
  --image-scanning-configuration scanOnPush=true \
  --region ap-northeast-1

# イメージスキャンを有効化してセキュリティ脆弱性を検出
```

#### イメージのプッシュ

```bash
# 1. ECRにログイン
aws ecr get-login-password --region ap-northeast-1 | \
  docker login --username AWS --password-stdin \
  123456789012.dkr.ecr.ap-northeast-1.amazonaws.com

# 2. イメージをビルド
docker build -t ktor-bff:latest .

# 3. タグを付ける
docker tag ktor-bff:latest \
  123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/ktor-bff:latest

docker tag ktor-bff:latest \
  123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/ktor-bff:${GIT_SHA}

# 4. プッシュ
docker push 123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/ktor-bff:latest
docker push 123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/ktor-bff:${GIT_SHA}
```

#### ライフサイクルポリシー

古いイメージを自動削除してコストを削減：

```json
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Keep last 10 images",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 10
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
```

---

### 8.5 ECS/Fargateの設定

#### タスク定義

```json
{
  "family": "ktor-bff",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::123456789012:role/ktorBffTaskRole",
  "containerDefinitions": [
    {
      "name": "ktor-bff",
      "image": "123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/ktor-bff:latest",
      "cpu": 512,
      "memory": 1024,
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "ENVIRONMENT",
          "value": "production"
        },
        {
          "name": "DB_HOST",
          "value": "ktor-db.xyz.ap-northeast-1.rds.amazonaws.com"
        },
        {
          "name": "REDIS_HOST",
          "value": "ktor-redis.xyz.cache.amazonaws.com"
        }
      ],
      "secrets": [
        {
          "name": "DB_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:ap-northeast-1:123456789012:secret:ktor-bff/db-password"
        },
        {
          "name": "JWT_SECRET",
          "valueFrom": "arn:aws:secretsmanager:ap-northeast-1:123456789012:secret:ktor-bff/jwt-secret"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/ktor-bff",
          "awslogs-region": "ap-northeast-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

#### CPU・メモリの選択

| 規模 | vCPU | メモリ | 用途 | コスト/月 |
|-----|------|--------|------|----------|
| **Tiny** | 0.25 | 512MB | 開発環境 | $7 |
| **Small** | 0.5 | 1GB | 小規模API | $15 |
| **Medium** | 1 | 2GB | 中規模API | $30 |
| **Large** | 2 | 4GB | 大規模API | $60 |
| **XLarge** | 4 | 8GB | 超大規模API | $120 |

**推奨**: 最初はSmall (0.5 vCPU, 1GB) からスタート

#### ECSサービスの作成

```bash
aws ecs create-service \
  --cluster ktor-cluster \
  --service-name ktor-service \
  --task-definition ktor-bff:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={
    subnets=[subnet-abc123,subnet-def456],
    securityGroups=[sg-xyz789],
    assignPublicIp=DISABLED
  }" \
  --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:ap-northeast-1:123456789012:targetgroup/ktor-tg/xyz,
    containerName=ktor-bff,
    containerPort=8080" \
  --health-check-grace-period-seconds 60
```

---

### 8.6 ALB（Application Load Balancer）

#### ALBの作成

```bash
aws elbv2 create-load-balancer \
  --name ktor-alb \
  --subnets subnet-abc123 subnet-def456 \
  --security-groups sg-alb123 \
  --scheme internet-facing \
  --type application \
  --ip-address-type ipv4
```

#### ターゲットグループ

```bash
aws elbv2 create-target-group \
  --name ktor-tg \
  --protocol HTTP \
  --port 8080 \
  --vpc-id vpc-xyz789 \
  --target-type ip \
  --health-check-enabled \
  --health-check-protocol HTTP \
  --health-check-path /health \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3
```

#### HTTPSリスナー

```bash
# HTTPS リスナーを作成
aws elbv2 create-listener \
  --load-balancer-arn arn:aws:elasticloadbalancing:ap-northeast-1:123456789012:loadbalancer/app/ktor-alb/xyz \
  --protocol HTTPS \
  --port 443 \
  --certificates CertificateArn=arn:aws:acm:ap-northeast-1:123456789012:certificate/xyz \
  --default-actions Type=forward,TargetGroupArn=arn:aws:elasticloadbalancing:ap-northeast-1:123456789012:targetgroup/ktor-tg/xyz

# HTTP → HTTPS リダイレクト
aws elbv2 create-listener \
  --load-balancer-arn arn:aws:elasticloadbalancing:ap-northeast-1:123456789012:loadbalancer/app/ktor-alb/xyz \
  --protocol HTTP \
  --port 80 \
  --default-actions Type=redirect,RedirectConfig="{Protocol=HTTPS,Port=443,StatusCode=HTTP_301}"
```

#### SSL/TLS証明書（AWS Certificate Manager）

```bash
# 証明書をリクエスト
aws acm request-certificate \
  --domain-name api.example.com \
  --subject-alternative-names "*.api.example.com" \
  --validation-method DNS \
  --region ap-northeast-1

# DNS検証用のCNAMEレコードを追加（Route 53）
# 証明書が発行されたらALBに関連付け
```

---

### 8.7 Auto Scaling

#### Target Tracking Scaling（推奨）

```json
{
  "TargetTrackingScalingPolicyConfiguration": {
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
    },
    "ScaleOutCooldown": 60,
    "ScaleInCooldown": 300
  }
}
```

```bash
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/ktor-cluster/ktor-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10

aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/ktor-cluster/ktor-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name ktor-cpu-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration file://scaling-policy.json
```

#### Step Scaling（高度な制御）

```json
{
  "AdjustmentType": "PercentChangeInCapacity",
  "MetricAggregationType": "Average",
  "Cooldown": 60,
  "StepAdjustments": [
    {
      "MetricIntervalLowerBound": 0,
      "MetricIntervalUpperBound": 10,
      "ScalingAdjustment": 10
    },
    {
      "MetricIntervalLowerBound": 10,
      "ScalingAdjustment": 30
    }
  ]
}
```

#### スケジュールベーススケーリング

```bash
# 平日の営業時間に増やす
aws application-autoscaling put-scheduled-action \
  --service-namespace ecs \
  --resource-id service/ktor-cluster/ktor-service \
  --scalable-dimension ecs:service:DesiredCount \
  --scheduled-action-name scale-up-business-hours \
  --schedule "cron(0 9 ? * MON-FRI *)" \
  --scalable-target-action MinCapacity=5,MaxCapacity=20

# 深夜に減らす
aws application-autoscaling put-scheduled-action \
  --service-namespace ecs \
  --resource-id service/ktor-cluster/ktor-service \
  --scalable-dimension ecs:service:DesiredCount \
  --scheduled-action-name scale-down-night \
  --schedule "cron(0 22 * * * *)" \
  --scalable-target-action MinCapacity=2,MaxCapacity=5
```

---

### 8.8 Secrets Manager

#### シークレットの作成

```bash
# データベースパスワード
aws secretsmanager create-secret \
  --name ktor-bff/db-password \
  --description "PostgreSQL password for Ktor BFF" \
  --secret-string "your-secure-password" \
  --region ap-northeast-1

# JWT Secret
aws secretsmanager create-secret \
  --name ktor-bff/jwt-secret \
  --description "JWT secret key" \
  --secret-string "your-jwt-secret-key" \
  --region ap-northeast-1

# OAuth Client Secret
aws secretsmanager create-secret \
  --name ktor-bff/oauth-client-secret \
  --description "OAuth client secret" \
  --secret-string "your-oauth-secret" \
  --region ap-northeast-1
```

#### Ktorアプリからの取得

```kotlin
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest

class SecretsManager(private val region: String) {
    private val client = SecretsManagerClient.builder()
        .region(Region.of(region))
        .build()
    
    fun getSecret(secretName: String): String {
        val request = GetSecretValueRequest.builder()
            .secretId(secretName)
            .build()
        
        val response = client.getSecretValue(request)
        return response.secretString()
    }
}

// 使用例
val secretsManager = SecretsManager("ap-northeast-1")
val dbPassword = secretsManager.getSecret("ktor-bff/db-password")
val jwtSecret = secretsManager.getSecret("ktor-bff/jwt-secret")
```

#### 環境変数での設定（推奨）

ECSタスク定義で環境変数として注入（前述のタスク定義参照）:

```json
"secrets": [
  {
    "name": "DB_PASSWORD",
    "valueFrom": "arn:aws:secretsmanager:ap-northeast-1:123456789012:secret:ktor-bff/db-password"
  }
]
```

Ktorアプリでは通常の環境変数として取得:

```kotlin
val dbPassword = System.getenv("DB_PASSWORD")
```

---

### 8.9 CloudWatch Logs & Metrics

#### ログの確認

```bash
# 最新ログを表示
aws logs tail /ecs/ktor-bff --follow

# 特定の期間のログを検索
aws logs filter-log-events \
  --log-group-name /ecs/ktor-bff \
  --start-time 1699000000000 \
  --end-time 1699100000000 \
  --filter-pattern "ERROR"

# Logs Insightsでクエリ
aws logs start-query \
  --log-group-name /ecs/ktor-bff \
  --start-time 1699000000 \
  --end-time 1699100000 \
  --query-string "fields @timestamp, @message | filter @message like /ERROR/ | sort @timestamp desc | limit 100"
```

#### カスタムメトリクス

```kotlin
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.*

class CloudWatchMetrics(private val namespace: String) {
    private val client = CloudWatchClient.builder()
        .region(Region.AP_NORTHEAST_1)
        .build()
    
    fun publishMetric(
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
val cloudWatch = CloudWatchMetrics("KtorBFF")

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

#### アラームの設定

```bash
# CPU使用率が80%を超えたらアラート
aws cloudwatch put-metric-alarm \
  --alarm-name ktor-high-cpu \
  --alarm-description "Alert when CPU exceeds 80%" \
  --metric-name CPUUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --dimensions Name=ServiceName,Value=ktor-service Name=ClusterName,Value=ktor-cluster \
  --alarm-actions arn:aws:sns:ap-northeast-1:123456789012:ktor-alerts

# エラー率が1%を超えたらアラート
aws cloudwatch put-metric-alarm \
  --alarm-name ktor-high-error-rate \
  --alarm-description "Alert when error rate exceeds 1%" \
  --metric-name ErrorRate \
  --namespace KtorBFF \
  --statistic Average \
  --period 60 \
  --threshold 1 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 3 \
  --alarm-actions arn:aws:sns:ap-northeast-1:123456789012:ktor-alerts
```

---

### 8.10 Blue-Green デプロイ

#### AWS CodeDeployの設定

```json
{
  "applicationName": "ktor-bff",
  "deploymentGroupName": "ktor-bff-dg",
  "serviceRoleArn": "arn:aws:iam::123456789012:role/CodeDeployServiceRole",
  "deploymentConfigName": "CodeDeployDefault.ECSAllAtOnce",
  "ecsServices": [
    {
      "serviceName": "ktor-service",
      "clusterName": "ktor-cluster"
    }
  ],
  "loadBalancerInfo": {
    "targetGroupPairInfoList": [
      {
        "targetGroups": [
          {
            "name": "ktor-tg-blue"
          },
          {
            "name": "ktor-tg-green"
          }
        ],
        "prodTrafficRoute": {
          "listenerArns": [
            "arn:aws:elasticloadbalancing:ap-northeast-1:123456789012:listener/app/ktor-alb/xyz/abc"
          ]
        }
      }
    ]
  },
  "autoRollbackConfiguration": {
    "enabled": true,
    "events": ["DEPLOYMENT_FAILURE", "DEPLOYMENT_STOP_ON_ALARM"]
  }
}
```

#### デプロイ戦略

**AllAtOnce**: 即座に全トラフィックを切り替え
```json
{
  "deploymentConfigName": "CodeDeployDefault.ECSAllAtOnce"
}
```

**Canary**: 段階的にトラフィックを移行
```json
{
  "deploymentConfigName": "CodeDeployDefault.ECSCanary10Percent5Minutes",
  "description": "10%を5分間、その後残り90%"
}
```

**Linear**: 線形にトラフィックを移行
```json
{
  "deploymentConfigName": "CodeDeployDefault.ECSLinear10PercentEvery1Minutes",
  "description": "1分ごとに10%ずつ移行"
}
```

---

## Chapter 9: モバイル最適化

### 9.1 ペイロード最適化

#### データサイズの削減

**バックエンドレスポンス**（冗長）:
```json
{
  "id": "user-123",
  "username": "johndoe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1-555-0123",
  "dateOfBirth": "1990-01-15",
  "address": {
    "street": "123 Main St",
    "city": "San Francisco",
    "state": "CA",
    "zipCode": "94102",
    "country": "USA"
  },
  "preferences": {
    "language": "en",
    "timezone": "America/Los_Angeles",
    "notificationsEnabled": true,
    "marketingEmailsEnabled": false
  },
  "metadata": {
    "lastLoginAt": "2025-11-03T10:30:00Z",
    "createdAt": "2023-01-15T08:00:00Z",
    "updatedAt": "2025-11-03T10:30:00Z"
  }
}
```

**モバイルレスポンス**（最適化）:
```json
{
  "id": "user-123",
  "name": "John Doe",
  "email": "john@example.com",
  "avatar": "https://cdn.example.com/avatars/user-123.jpg"
}
```

**削減率**: 約85%

#### フィールドの選択的返却

```kotlin
@Serializable
data class UserDetailLevel {
    val id: String
    val name: String
    val email: String
    val avatar: String
}

@Serializable
data class UserMinimalLevel {
    val id: String
    val name: String
}

// クエリパラメータで制御
get("/api/v1/users/{id}") {
    val userId = call.parameters["id"]!!
    val level = call.request.queryParameters["level"] ?: "detail"
    
    val user = userService.getUser(userId)
    
    val response = when (level) {
        "minimal" -> UserMinimalLevel(user.id, user.name)
        else -> UserDetailLevel(user.id, user.name, user.email, user.avatar)
    }
    
    call.respond(response)
}
```

---

### 9.2 APIバージョニング

#### URLパスベースバージョニング（推奨）

```kotlin
routing {
    route("/api/v1") {
        userRoutesV1()
        orderRoutesV1()
    }
    
    route("/api/v2") {
        userRoutesV2()
        orderRoutesV2()
    }
}

fun Route.userRoutesV1() {
    get("/users/{id}") {
        // V1の実装
        val user = userService.getUser(userId)
        call.respond(user.toV1Response())
    }
}

fun Route.userRoutesV2() {
    get("/users/{id}") {
        // V2の実装（新しいフィールド追加）
        val user = userService.getUser(userId)
        call.respond(user.toV2Response())
    }
}
```

#### 非推奨バージョンの管理

```kotlin
fun Route.userRoutesV1() {
    get("/users/{id}") {
        // 非推奨ヘッダーを追加
        call.response.header("Sunset", "Sun, 01 Jun 2025 00:00:00 GMT")
        call.response.header("Link", "</api/v2/users/{id}>; rel=\"successor-version\"")
        call.response.header("Deprecation", "true")
        
        val user = userService.getUser(userId)
        call.respond(user.toV1Response())
    }
}
```

#### バージョン使用状況の追跡

```kotlin
install(CallLogging) {
    mdc("apiVersion") { call ->
        call.request.path().split("/").getOrNull(2) ?: "unknown"
    }
}

// CloudWatchメトリクスに送信
cloudWatch.publishMetric(
    metricName = "APIVersionUsage",
    value = 1.0,
    dimensions = mapOf(
        "Version" to apiVersion,
        "Endpoint" to endpoint
    )
)
```

---

### 9.3 Gzip圧縮

#### Compressionプラグイン

```kotlin
install(Compression) {
    gzip {
        priority = 1.0
        minimumSize(1024)  // 1KB以上のレスポンスを圧縮
        
        // 圧縮するContent-Type
        matchContentType(
            ContentType.Application.Json,
            ContentType.Text.Plain,
            ContentType.Text.Html
        )
        
        // 圧縮しないパス
        excludePath("/health")
        excludePath("/metrics")
    }
    
    deflate {
        priority = 10.0
        minimumSize(1024)
    }
}
```

**効果**:
- JSONレスポンス: 70-90%削減
- HTMLレスポンス: 50-80%削減

---

### 9.4 画像最適化

#### サムネイル生成

```kotlin
fun generateThumbnailUrl(originalUrl: String, width: Int, height: Int): String {
    // CloudFront + Lambda@Edgeでリサイズ
    return "$originalUrl?w=$width&h=$height&format=webp"
}

@Serializable
data class MobileProduct(
    val id: String,
    val name: String,
    val thumbnail: String,  // サムネイルURL
    val fullImage: String   // フルサイズURL
)

fun Product.toMobileView() = MobileProduct(
    id = id,
    name = name,
    thumbnail = generateThumbnailUrl(imageUrl, 300, 300),
    fullImage = imageUrl
)
```

#### WebP形式への変換

```kotlin
// CloudFrontのカスタムヘッダー
call.response.header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")

// Lambda@EdgeまたはCloudFront Functionsで変換
```

---

### 9.5 キャッシング戦略

#### Cache-Controlヘッダー

```kotlin
get("/api/v1/products") {
    val products = productService.getProducts()
    
    // 5分間キャッシュ
    call.response.header(
        "Cache-Control",
        "public, max-age=300, s-maxage=300"
    )
    
    // ETag
    val etag = products.hashCode().toString()
    call.response.header("ETag", etag)
    
    // クライアントのETagと比較
    if (call.request.header("If-None-Match") == etag) {
        call.respond(HttpStatusCode.NotModified)
        return@get
    }
    
    call.respond(products)
}
```

#### CDN統合（CloudFront）

```
モバイルアプリ
  ↓
CloudFront（キャッシュ）
  ↓ キャッシュミス
ALB
  ↓
ECS/Fargate（Ktorアプリ）
```

**CloudFront設定**:
```json
{
  "DefaultCacheBehavior": {
    "TargetOriginId": "ktor-alb",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"],
    "CachedMethods": ["GET", "HEAD", "OPTIONS"],
    "Compress": true,
    "DefaultTTL": 300,
    "MaxTTL": 3600,
    "MinTTL": 0,
    "ForwardedValues": {
      "QueryString": true,
      "Headers": ["Authorization", "Accept", "Accept-Language"]
    }
  }
}
```

---

### 9.6 レート制限とスロットリング

#### ユーザー別レート制限

```kotlin
install(RateLimit) {
    register(RateLimitName("api")) {
        rateLimiter(limit = 100, refillPeriod = 60.seconds)
        requestKey { call ->
            call.principal<JWTPrincipal>()
                ?.payload
                ?.getClaim("user_id")
                ?.asString()
                ?: call.request.origin.remoteAddress
        }
    }
    
    register(RateLimitName("premium")) {
        rateLimiter(limit = 1000, refillPeriod = 60.seconds)
        requestKey { call ->
            val principal = call.principal<JWTPrincipal>()
            val plan = principal?.payload?.getClaim("plan")?.asString()
            
            if (plan == "premium") {
                principal.payload.getClaim("user_id").asString()
            } else {
                null  // プレミアムユーザーのみ適用
            }
        }
    }
}
```

---

### 9.7 オフライン同期

#### デルタAPIの実装

```kotlin
@Serializable
data class DeltaResponse<T>(
    val items: List<T>,
    val syncToken: String,
    val hasMore: Boolean
)

get("/api/v1/orders/delta") {
    val userId = getUserIdFromToken(call)
    val since = call.request.queryParameters["since"]
    
    val (orders, newSyncToken) = if (since != null) {
        // 差分のみ取得
        orderService.getOrdersSince(userId, since)
    } else {
        // 初回同期（全件）
        orderService.getAllOrders(userId)
    }
    
    call.respond(DeltaResponse(
        items = orders,
        syncToken = newSyncToken,
        hasMore = false
    ))
}
```

#### 競合解決

```kotlin
@Serializable
data class OrderUpdate(
    val id: String,
    val version: Int,  // 楽観的ロック
    val quantity: Int
)

put("/api/v1/orders/{id}") {
    val orderId = call.parameters["id"]!!
    val update = call.receive<OrderUpdate>()
    
    try {
        val updatedOrder = orderService.updateOrder(orderId, update)
        call.respond(updatedOrder)
    } catch (e: OptimisticLockException) {
        call.respond(
            HttpStatusCode.Conflict,
            ErrorResponse(
                "CONFLICT",
                "Order was modified by another user",
                mapOf("currentVersion" to e.currentVersion.toString())
            )
        )
    }
}
```

---

### 9.8 モバイル固有のエラーハンドリング

#### リトライ可能なエラー

```kotlin
@Serializable
data class MobileErrorResponse(
    val code: String,
    val message: String,
    val retryable: Boolean,  // モバイルアプリがリトライすべきか
    val retryAfter: Int? = null,  // 秒数
    val details: Map<String, String>? = null
)

install(StatusPages) {
    exception<TimeoutException> { call, cause ->
        call.respond(
            HttpStatusCode.GatewayTimeout,
            MobileErrorResponse(
                code = "TIMEOUT",
                message = "Request timeout",
                retryable = true,
                retryAfter = 5
            )
        )
    }
    
    exception<BackendException> { call, cause ->
        call.respond(
            HttpStatusCode.BadGateway,
            MobileErrorResponse(
                code = "BACKEND_ERROR",
                message = "Backend service unavailable",
                retryable = true,
                retryAfter = 30
            )
        )
    }
    
    exception<ValidationException> { call, cause ->
        call.respond(
            HttpStatusCode.BadRequest,
            MobileErrorResponse(
                code = "VALIDATION_ERROR",
                message = cause.message ?: "Validation failed",
                retryable = false
            )
        )
    }
}
```

---

## Chapter 10: コスト最適化

### 10.1 Fargate Spot

#### Spot インスタンスの使用

```json
{
  "capacityProviders": [
    "FARGATE",
    "FARGATE_SPOT"
  ],
  "defaultCapacityProviderStrategy": [
    {
      "capacityProvider": "FARGATE_SPOT",
      "weight": 4,
      "base": 0
    },
    {
      "capacityProvider": "FARGATE",
      "weight": 1,
      "base": 2
    }
  ]
}
```

**コスト削減**: 最大70%削減（ただし中断のリスクあり）

**推奨**: 開発/ステージング環境でSpotを使用、本番環境は通常のFargateを使用

---

### 10.2 Compute Savings Plans

#### Savings Plansの購入

```
1年契約、全額前払い: 52% OFF
1年契約、一部前払い: 38% OFF
1年契約、前払いなし: 31% OFF

3年契約、全額前払い: 66% OFF
3年契約、一部前払い: 49% OFF
3年契約、前払いなし: 43% OFF
```

**推奨**: 1年契約、全額前払いでベースラインの50%をカバー

---

### 10.3 スケジューリング

#### 非本番環境の停止

```bash
# Lambda関数で平日夜と週末にECSタスクを0にする
aws ecs update-service \
  --cluster ktor-cluster-dev \
  --service ktor-service-dev \
  --desired-count 0

# 平日朝に再開
aws ecs update-service \
  --cluster ktor-cluster-dev \
  --service ktor-service-dev \
  --desired-count 2
```

**コスト削減**: 開発環境で約70%削減（週5日×9時間のみ稼働）

---

## まとめ

この章で学んだこと:

1. ✅ **AWS ECS/Fargateデプロイ**
   - VPC構成
   - セキュリティグループ
   - タスク定義
   - ALB設定

2. ✅ **Auto Scaling**
   - Target Tracking
   - Step Scaling
   - スケジュールベース

3. ✅ **モニタリング**
   - CloudWatch Logs
   - カスタムメトリクス
   - アラーム設定

4. ✅ **モバイル最適化**
   - ペイロード削減（85%）
   - APIバージョニング
   - Gzip圧縮
   - キャッシング

5. ✅ **コスト最適化**
   - Fargate Spot（70% OFF）
   - Savings Plans（52% OFF）
   - スケジューリング（70% OFF）

---

### 最終チェックリスト

**デプロイ前**:
- [ ] VPCとサブネットを作成
- [ ] セキュリティグループを設定
- [ ] ECRリポジトリを作成
- [ ] Secrets Managerにシークレットを登録
- [ ] IAMロールを作成
- [ ] RDSとElastiCacheをセットアップ

**デプロイ**:
- [ ] Dockerイメージをビルド・プッシュ
- [ ] ECSタスク定義を作成
- [ ] ALBを作成・設定
- [ ] ECSサービスを作成
- [ ] Auto Scalingを設定

**デプロイ後**:
- [ ] ヘルスチェックが成功する
- [ ] ログがCloudWatchに出力される
- [ ] メトリクスが収集される
- [ ] アラームが動作する
- [ ] HTTPSでアクセスできる

**おめでとうございます！🎉**

これで、Kotlin + KtorでプロダクションレベルのモバイルBFFを構築し、AWSにデプロイする全工程を学びました。

---

### 次のステップ

- 実際にモバイルアプリと統合
- パフォーマンスチューニング
- セキュリティ監査
- DR（ディザスタリカバリ）計画
- マルチリージョン展開
