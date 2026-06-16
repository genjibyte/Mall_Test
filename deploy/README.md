# mall-swarm 本地测试环境（E:\Mall_Test\deploy）

为接口自动化测试搭建的本地全栈部署。**不修改 `mall-swarm` 源码**：基础设施跑在隔离端口的 Docker 容器中，
服务以 `dev` profile 启动并通过 OS 环境变量把 infra 端点指向这些隔离端口（OS env 优先级高于 application.yml 和 Nacos 配置）。

## 一、统一入口

| 用途 | 地址 | 备注 |
|---|---|---|
| **API 网关（测试入口）** | http://localhost:8201 | 所有业务接口都经此，路径前缀 `/mall-admin` `/mall-portal` `/mall-search` `/mall-auth` |
| 聚合 API 文档（Knife4j） | http://localhost:8201/doc.html | 自动聚合各服务 OpenAPI，建框架时用来发现接口 |
| Nacos 控制台 | http://localhost:18848/nacos | 账号 nacos / nacos |
| RabbitMQ 控制台 | http://localhost:15673 | 账号 mall / mall，vhost `/mall` |

## 二、端口映射（宿主机默认端口被其他 QA 栈占用，故全部隔离）

### 基础设施（Docker，`docker-compose-env.yml`）
| 组件 | 容器 | 宿主机端口 | 容器内端口 |
|---|---|---|---|
| MySQL 5.7 | mall-mysql | **23306** | 3306 |
| Redis 7 | mall-redis | **16379** | 6379 |
| Nacos 2.1.0 | mall-nacos | **18848** / 19848(gRPC) | 8848 / 9848 |
| RabbitMQ 3.9 | mall-rabbitmq | **5673** / 15673 | 5672 / 15672 |
| Elasticsearch 7.17.3 | mall-elasticsearch | **19200** / 19300 | 9200 / 9300 |
| MongoDB 4 | mall-mongo | **27018** | 27017 |

### 服务（Maven 构建的 jar，本机 JVM 运行）
| 服务 | 端口 | 说明 |
|---|---|---|
| mall-gateway | 8201 | 网关，测试入口 |
| mall-auth | 8401 | 统一认证（按 clientId 委托 admin/portal） |
| mall-admin | **18080** | 默认 8080 被 alsys-backend 占用，已重映射 |
| mall-portal | 8085 | 移动端商城 |
| mall-search | 8081 | ES 搜索 |

> 重映射对测试透明：网关按 Nacos 服务名路由，admin 改端口不影响 `:8201` 入口。

## 三、测试账号（来自 mall.sql 种子数据）
| 类型 | 用户名 | 密码 | 登录接口 |
|---|---|---|---|
| 管理员 | `admin` | `macro123` | `POST /mall-admin/admin/login`（JSON body） |
| 会员 | `test` | `123456` | `POST /mall-portal/sso/login`（form） |

返回 `data.token`，调用受保护接口时带请求头 `Authorization: Bearer <token>`。
统一响应体 `{code,message,data}`，`code` 为业务码（200/401/403/404/500），与 HTTP 状态码解耦——断言要校验 `body.code`。

## 四、操作

```powershell
# 启动基础设施
docker compose -f E:\Mall_Test\deploy\docker-compose-env.yml up -d
# 构建（首次，跳过 docker 镜像插件）
cd E:\Mall_Test\mall-swarm; mvn clean install -DskipTests -Ddocker.skip=true
# 启动 / 停止服务
powershell -File E:\Mall_Test\deploy\run-services.ps1
powershell -File E:\Mall_Test\deploy\stop-services.ps1
# 关停基础设施（保留数据卷）        / 连数据一起清掉
docker compose -f E:\Mall_Test\deploy\docker-compose-env.yml down
docker compose -f E:\Mall_Test\deploy\docker-compose-env.yml down -v
```

服务日志：`E:\Mall_Test\deploy\logs\*.log`（`*.err.log` 为 stderr）。

## 五、已知注意点
- **ES IK 分词插件**：mall-search 启动时会用 `ik_max_word` 创建 `pms` 索引，必须安装 IK 插件。本环境已装入
  运行中的 ES 容器（`analysis-ik`），可经 `docker restart` 保留，但**`compose down` 重建容器后会丢失**
  （compose 未挂载 plugins 卷）。重建后需重新执行：
  `docker exec mall-elasticsearch elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/7.17.3` 然后 `docker restart mall-elasticsearch`。
- **搜索数据**：ES 初始为空，需 `POST /mall-search/esProduct/importAll` 把数据库商品导入（已执行过，导入 20 条上架商品）。
- **MinIO / 阿里云 OSS / 支付宝**：未部署/为占位配置。文件上传、真实支付类接口会失败，不影响其他业务链路。
- **mall-monitor / mall-demo**：未启动（监控/教学模块，非测试目标）。
