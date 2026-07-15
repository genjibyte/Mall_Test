# 项目状态 / 交接快照（PROJECT-STATUS）

> 单文件交接：读完即可恢复上下文并继续。最后更新 2026-07-16。
> 详细约束见 [context-pack/](context-pack/)，覆盖矩阵见 [mall-api-test/docs/test-coverage.md](mall-api-test/docs/test-coverage.md)，审计见 [mall-api-test/docs/audit.md](mall-api-test/docs/audit.md)，工程收口见 [mall-api-test/docs/engineering-closure.md](mall-api-test/docs/engineering-closure.md)，审计链路见 [mall-api-test/docs/audit-chain-design.md](mall-api-test/docs/audit-chain-design.md)。
> **下单主链路深度样板**：[mall-api-test/docs/order-chain-exemplar.md](mall-api-test/docs/order-chain-exemplar.md)。

## 1. 项目是什么

为微服务电商系统 **mall-swarm**（Spring Cloud Alibaba 2025 / Boot 3.5 / Sa-Token / Java 17）搭建的**接口自动化测试工程**：含设计约束包、测试框架、本地部署。被测源码不入库（Apache-2.0，单独克隆）。
GitHub：https://github.com/genjibyte/Mall_Test （`main`，39+ 提交）。

## 2. 目录结构

| 目录 | 内容 |
|---|---|
| `context-pack/` | 设计/约束包 9 维（目标/边界/代码/业务/运行环境/质量门禁/badcase/交接/CICD设计）|
| `mall-api-test/` | 测试框架（Java+JUnit5+RestAssured+Allure），70 用例（默认 69:59绿+10跳[6 KnownDefect + 1 维护 + 3 混沌] + 1 @slow MQ）|
| `deploy/` | 隔离端口基础设施 compose + 服务启停脚本 |
| `mall-swarm/` | 被测系统克隆（gitignore，不入库）|

## 3. 已完成（全貌）

1. **架构分析**：5 模块职责、服务依赖、Sa-Token 双账号鉴权、下单链路源码级研究。
2. **本地部署**：6 容器(MySQL/Redis/Nacos/RabbitMQ/ES/Mongo)隔离端口 + 5 服务 dev 运行，全链路验证通过。
3. **设计**：context-pack 9 文件 + CI/CD 设计预案（未实施）+ 工程收口/审计链路设计。
4. **测试框架**：分层 client/flow/fixture/core/cases；**70 用例（默认 69：59 通过 + 10 跳过；另 1 @slow MQ）** 覆盖 5 核心链路 + 浏览/会员注册认证/购物车 + 跨服务 + 后台管理(商品批量状态/审核/软删/订单关闭) + 退货售后(申请→确认/拒绝) + 搜索综合筛选排序 + 测试基建(库存完整性守卫 + 专用隔离SKU下单) + MQ 真实延迟 + 折扣边界/等价类 + 后台 API 造券生命周期。**企业化**:Allure 环境/分类/Severity/Owner/Issue 可追溯 + 测试规范 + README 门面 + 质量门禁指标 + `runId/ciBuildId/knownDefectIds` 审计字段。
5. **缺陷发现**：R1 非幂等支付、R2 越权支付、R4 并发超卖、**R6 积分不退**、**R8 管理员关单不退锁库存(库存泄漏)**、**R9 优惠券并发领取超发**——均为 @KnownDefect 探针；另 R3 非事务、R7 空车clear500 已记录。
6. **H1 数据漂移治理**：DataHygieneFixture + DataIntegrityTest(常驻守卫) + DataMaintenanceTest(手动)；已清理负库存 4→0、软删购物车 243→0。**会员+商品双隔离**(IsolatedMemberFixture+IsolatedSkuFixture)落地,隔离会员订单可整体回收;剩既有用例迁移 + Testcontainers/并行。
7. **M3 MQ 真实延迟超时**：OrderTimeoutMqTest(@Tag slow，默认排除、`-Pslow` 跑)，真实 TTL队列→DLX→CancelOrderReceiver，实跑 ~60s 通过；RabbitFixture 清队列解决按消息 TTL 队头阻塞。

## 4. 运行环境（隔离端口）

| 组件 | 端口 | | 服务 | 端口 |
|---|---|---|---|---|
| 网关(测试入口) | **8201** | | mall-gateway | 8201 |
| MySQL | 23306 | | mall-auth | 8401 |
| Redis | 16379 | | mall-admin | **18080** |
| Nacos | 18848/19848 | | mall-portal | 8085 |
| RabbitMQ | 5673/15673 | | mall-search | 8081 |
| ES | 19200 | | | |
| Mongo | 27018 | | | |

账号：admin/macro123（管理员）、test/123456（会员）。统一入口 http://localhost:8201 ，token 走 `Authorization: Bearer`。

## 5. 如何恢复并继续

```bash
# 1. 起基础设施（若未运行）
docker compose -f deploy/docker-compose-env.yml up -d
# 2. 构建被测系统（必须 -Ddocker.skip=true）并起服务
cd mall-swarm && mvn clean install -DskipTests -Ddocker.skip=true && cd ..
powershell -File deploy/run-services.ps1
# 3. 健康自检 + 跑测试
curl -s http://localhost:18848/nacos/v1/ns/catalog/services   # 5 服务健康
cd mall-api-test && mvn test                                   # 默认 69 用例全绿(10 跳过；slow 排除)
cd mall-api-test && powershell -ExecutionPolicy Bypass -File tools/run-quality-gate.ps1  # 生成质量指标与审计字段
# mvn test -Pslow                                              # 含 MQ 真实延迟用例(约 60s，夜间/全量)
```

环境踩坑（已固化解决，详见 context-pack/06）：ES 需装 IK 插件、Nacos 2.x 需映射 gRPC 19848、构建需 `-Ddocker.skip=true`、PS 脚本须 ASCII。

> ✅ **ES on WSL2 cgroup v2 —— 已解决（2026-06-19）**：`wsl --update` 切到 cgroup v2 后 **ES 7.17.3 自带 JDK `CgroupV2Subsystem` NPE 崩溃**（`anyController is null`）。**修法（已施加，compose 已 pin）：ES 镜像升到 `7.17.18`（自带 JDK 已修该 bug）+ IK `7.17.18`**（`docker exec mall-elasticsearch elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/7.17.18` → 重启 ES → `importAll`）。搜索 9 用例已全绿，默认 fast 门禁 69 用例闭环。仅测试环境补丁版差异（SUT 原 pin 7.17.3），搜索语义不变。
> **实测无效、勿重复**：`.wslconfig systemd.unified_cgroup_hierarchy=0`（docker-desktop 分发非 systemd 忽略）、compose `cgroup: host`、`JAVA_TOOL_OPTIONS=-XX:-UseContainerSupport`（ES 不透传给启动器）。
>
> **Docker/WSL 卡死恢复**：若 Docker 引擎起不来且 `wsl -l -v` 显示 `docker-desktop` 长期 Stopped → `wsl --update` + 退出 Docker + `wsl --shutdown` + 重启 Docker（本会话据此恢复）。

## 6. 审计要点（详见 audit.md）

- **H1 测试数据隔离/漂移（已部分治理）**：曾负库存4个/243软删购物车行已清；新增库存完整性守卫(DataIntegrityTest 常驻绿)+维护工具(DataMaintenanceTest)。剩余：完全隔离(独立账号/Testcontainers)+开并行；H2：5会员密码被改未还原。
- 其余：~~MQ 真实延迟路径(M3)~~**已覆盖**、admin 覆盖**部分补齐**(M5：商品批量状态/订单关闭已覆盖，剩审核/退货/批量删除)、硬编码锚点(M1)、并行禁用(M2)。

## 7. 下一步建议（按价值）

> **后续链路路线图（去重驱动、按风险分类）见 [mall-api-test/docs/test-roadmap.md](mall-api-test/docs/test-roadmap.md)**：
> P1 会员注册认证、营销券生命周期已落地；后续按 RBAC 配置 → 退款会计/聚合传播 → Mongo 社交；秒杀仅做展示闭环，不做假下单并发。

1. **后续不同链路**：按 test-roadmap.md（不同服务 + 多类真实风险 + 每用例独立讲述价值）。
2. **H1 续**：既有库存类用例渐进迁移到 Isolated* 夹具 + Testcontainers 全新库 + 开并行（双隔离夹具/守卫/维护/回收已就绪）。
3. 落地 CI（context-pack/08 设计已就绪；slow 用例走 nightly `-Pslow`）。

## 8. Agent 长期记忆指针

`~/.claude/projects/E--Mall-Test/memory/`：mall-swarm-test-framework / mall-swarm-local-deployment / order-chain-findings（含 R1–R8 与数据锚点）。
