# 04 · 运行环境（Runtime Environment）

> 本地已部署的全栈测试环境（2026-06 实测可用）。完整细节见 [../deploy/README.md](../deploy/README.md)。
> 设计原则：基础设施跑在 Docker 隔离端口；服务以 `dev` profile 本机 JVM 运行；**不改 mall-swarm 源码**，靠 OS 环境变量覆盖端点。

## 统一入口与控制台

| 用途 | 地址 | 备注 |
|---|---|---|
| **API 网关（测试入口）** | http://localhost:8201 | 所有接口经此，路径前缀 `/mall-admin` `/mall-portal` `/mall-search` `/mall-auth` |
| 聚合 API 文档(Knife4j) | http://localhost:8201/doc.html | 发现接口/查参数 |
| Nacos 控制台 | http://localhost:18848/nacos | nacos / nacos |
| RabbitMQ 控制台 | http://localhost:15673 | mall / mall（vhost `/mall`） |

## 端口映射（默认端口被本机其它 QA 栈占用 → 全部隔离）

### 基础设施（Docker，[../deploy/docker-compose-env.yml](../deploy/docker-compose-env.yml)）
| 组件 | 容器名 | 宿主端口 | 容器端口 |
|---|---|---|---|
| MySQL 5.7 | mall-mysql | **23306** | 3306 |
| Redis 7 | mall-redis | **16379** | 6379 |
| Nacos 2.1.0 | mall-nacos | **18848** / 19848(gRPC) | 8848 / 9848 |
| RabbitMQ 3.9 | mall-rabbitmq | **5673** / 15673 | 5672 / 15672 |
| Elasticsearch 7.17.3 | mall-elasticsearch | **19200** / 19300 | 9200 / 9300 |
| MongoDB 4 | mall-mongo | **27018** | 27017 |

### 服务（Maven 构建 jar，本机 JVM）
| 服务 | 端口 | 备注 |
|---|---|---|
| mall-gateway | 8201 | 入口 |
| mall-auth | 8401 | |
| mall-admin | **18080** | 默认 8080 被 alsys-backend 占用，重映射 |
| mall-portal | 8085 | |
| mall-search | 8081 | |

> Nacos 2.x 客户端按 `httpPort+1000` 推导 gRPC（18848→19848），故 19848 必须映射，否则注册/拉配置失败。
> 服务重映射对测试**透明**：网关按 Nacos 服务名路由，仍从 `:8201` 进。

## 测试账号（来自 mall.sql）

| 类型 | 用户名 | 密码 | 登录接口 |
|---|---|---|---|
| 管理员 | `admin` | `macro123` | `POST /mall-admin/admin/login`（JSON body） |
| 会员 | `test` | `123456` | `POST /mall-portal/sso/login`（form） |

返回 `data.token`；调受保护接口带头 `Authorization: Bearer <token>`。admin/member 两套 token 不通用。

## 启停与构建

```powershell
# 1) 基础设施
docker compose -f E:\Mall_Test\deploy\docker-compose-env.yml up -d
# 2) 构建（首次；必须带 -Ddocker.skip=true，否则 fabric8 插件连不可达的远程 dockerHost 会失败）
cd E:\Mall_Test\mall-swarm; mvn clean install -DskipTests -Ddocker.skip=true
# 3) 服务启停
powershell -File E:\Mall_Test\deploy\run-services.ps1     # 启动 5 个服务
powershell -File E:\Mall_Test\deploy\stop-services.ps1     # 停止
# 4) 关停基础设施（保留卷 / 连数据清空）
docker compose -f E:\Mall_Test\deploy\docker-compose-env.yml down       # 保数据
docker compose -f E:\Mall_Test\deploy\docker-compose-env.yml down -v    # 清数据
```

服务日志：`E:\Mall_Test\deploy\logs\*.log`（`*.err.log` 为 stderr）。`run-services.ps1` 内置 OS env 覆盖（Nacos/MySQL/Redis/Mongo/RabbitMQ/ES 指向上表隔离端口）。

## 健康自检（开测前）

```bash
curl -s http://localhost:18848/nacos/v1/ns/catalog/services      # 应见 5 个服务各 1 健康实例
curl -s http://localhost:19200/_cluster/health                   # status=green
curl -sX POST http://localhost:8201/mall-admin/admin/login -H "Content-Type: application/json" -d '{"username":"admin","password":"macro123"}'  # code:200 + token
```

## 已知运行约束（详见 [06-historical-badcases.md](06-historical-badcases.md)）

- **ES IK 插件**：mall-search 启动需 `ik_max_word`。已装入运行中的 ES 容器，但**未挂卷**——`compose down` 重建后丢失，需重装并重启 ES。
- **搜索数据**：ES 初始为空，先 `POST /mall-search/esProduct/importAll` 导入（已导入 20 条上架商品）。
- **MinIO/OSS/支付宝**：未部署/占位 → 文件上传与真实支付接口会失败，测试需跳过或打桩。
- **数据重置**：`down -v` 后需重新 `up` → 导 `mall.sql` → 导 Nacos 配置 → 装 IK → importAll。

## 数据准备/校验直连

- MySQL：`docker exec mall-mysql mysql -uroot -proot mall -e "..."`（或宿主 `localhost:23306` root/root）。
- Redis：`docker exec mall-redis redis-cli`（order 自增号、Sa-Token 会话、`auth:pathResourceMap`）。
