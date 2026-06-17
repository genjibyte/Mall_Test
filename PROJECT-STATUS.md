# 项目状态 / 交接快照（PROJECT-STATUS）

> 单文件交接：读完即可恢复上下文并继续。最后更新 2026-06-17。
> 详细约束见 [context-pack/](context-pack/)，覆盖矩阵见 [mall-api-test/docs/test-coverage.md](mall-api-test/docs/test-coverage.md)，审计见 [mall-api-test/docs/audit.md](mall-api-test/docs/audit.md)。

## 1. 项目是什么

为微服务电商系统 **mall-swarm**（Spring Cloud Alibaba 2025 / Boot 3.5 / Sa-Token / Java 17）搭建的**接口自动化测试工程**：含设计约束包、测试框架、本地部署。被测源码不入库（Apache-2.0，单独克隆）。
GitHub：https://github.com/genjibyte/Mall_Test （`main`，19 提交）。

## 2. 目录结构

| 目录 | 内容 |
|---|---|
| `context-pack/` | 设计/约束包 9 维（目标/边界/代码/业务/运行环境/质量门禁/badcase/交接/CICD设计）|
| `mall-api-test/` | 测试框架（Java+JUnit5+RestAssured+Allure），41 用例 |
| `deploy/` | 隔离端口基础设施 compose + 服务启停脚本 |
| `mall-swarm/` | 被测系统克隆（gitignore，不入库）|

## 3. 已完成（全貌）

1. **架构分析**：5 模块职责、服务依赖、Sa-Token 双账号鉴权、下单链路源码级研究。
2. **本地部署**：6 容器(MySQL/Redis/Nacos/RabbitMQ/ES/Mongo)隔离端口 + 5 服务 dev 运行，全链路验证通过。
3. **设计**：context-pack 9 文件 + CI/CD 设计预案（未实施）。
4. **测试框架**：分层 client/flow/fixture/core/cases；**41 用例（36 通过 + 5 @KnownDefect 跳过）** 覆盖 5 核心链路 + 浏览/会员/购物车 + 跨服务 + 后台管理(商品批量状态/订单关闭)。
5. **缺陷发现**：R1 非幂等支付、R2 越权支付、R4 并发超卖、**R6 积分不退**、**R8 管理员关单不退锁库存(库存泄漏)**——均为 @KnownDefect 探针；另 R3 非事务、R7 空车clear500 已记录。

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
cd mall-api-test && mvn test                                   # 41 用例全绿(5 跳过)
```

环境踩坑（已固化解决，详见 context-pack/06）：ES 需装 IK 插件、Nacos 2.x 需映射 gRPC 19848、构建需 `-Ddocker.skip=true`、PS 脚本须 ASCII。

## 6. 审计要点（详见 audit.md）

- **最大技术债：测试数据隔离/漂移**（H1：负库存4个、test累计171订单/226软删购物车行；H2：5会员密码被改未还原）。扩规模/开并行前应优先治理（建议每用例 DB 事务回滚或独立测试账号）。
- 其余：MQ 真实延迟路径未测(M3)、admin 覆盖**部分补齐**(M5：商品批量状态/订单关闭已覆盖，剩审核/退货/批量删除)、硬编码锚点(M1)、并行禁用(M2)。

## 7. 下一步建议（按价值）

1. **治理测试数据隔离**（H1/H2）——专用测试账号/商品 + 事务回滚 + suite teardown 清理。
2. MQ 超时取消 Awaitility @Tag("slow") 用例（M3）。
3. admin 管理端链路续：审核(verifyStatus)、退货审核、批量删除/恢复（M5 剩余；批量上下架/新品/推荐/关单已覆盖）。
4. 会员注册(短信码)、搜索综合筛选、R3 非事务探针。
5. 落地 CI（context-pack/08 设计已就绪）。

## 8. Agent 长期记忆指针

`~/.claude/projects/E--Mall-Test/memory/`：mall-swarm-test-framework / mall-swarm-local-deployment / order-chain-findings（含 R1–R8 与数据锚点）。
