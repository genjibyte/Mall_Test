# Mall_Test · mall-swarm 接口自动化测试工程

针对微服务电商系统 [mall-swarm](https://github.com/macrozheng/mall-swarm)（Spring Cloud Alibaba 2025 / Spring Boot 3.5 / Sa-Token / Java 17）的**企业级接口自动化测试**工程：设计约束包 + 分层测试框架 + 隔离端口本地部署，覆盖 5 大业务链路并以源码级证据发现真实缺陷。

> 被测系统 `mall-swarm` 源码**不纳入本仓库**（Apache-2.0，单独克隆）。本仓库是围绕它的测试资产。

## 亮点

- **60 个用例**：默认 `mvn test` 跑 59（53 通过 + 6 跳过）+ 1 个 `@Tag("slow")` MQ 真实延迟用例（`-Pslow`）。企业级 Allure 报告（环境/分类/Severity/Owner/Issue 可追溯）。
- **5 大链路 + 扩展**：下单主链路、认证&RBAC、超时取消、商品搜索、优惠券营销 + 后台管理 / 退货售后 / 搜索筛选 / 跨服务端到端。
- **证据驱动 oracle**：促销/金额预期复刻源码算法从 DB 配置现算，**非魔法数**；副作用直连 MySQL 灰盒复核。
- **缺陷发现并固化**：R1 非幂等支付、R2 越权支付、R4 并发超卖、R6 积分不退、R8 关单不退锁库存 —— 以 `@KnownDefect` 探针按"正确行为"断言、验证后默认跳过、不阻断门禁，并 `@Issue` 可追溯。
- **可复现性工程**：三种数据策略 + 专用会员/商品**隔离夹具** + **库存完整性常驻守卫** + 数据卫生维护/回收。
- **报告工程化**：Allure 环境面板 + 失败分类 + Severity/Owner/Story/Issue 可追溯；HTTP 请求响应自动抓取。

## 目录

| 目录 | 内容 |
|---|---|
| [context-pack/](context-pack/) | **设计 / 约束上下文包**（9 维：目标/边界/代码/业务/运行环境/质量门禁/badcase/交接/CICD 设计）。冷启动先读这里。 |
| [mall-api-test/](mall-api-test/) | **测试框架（代码）**：Java + JUnit5 + RestAssured + Allure，分层 config/core/auth/client/flow/fixture/cases/support。 |
| [deploy/](deploy/) | **本地部署**：隔离端口基础设施 docker-compose + 服务启停脚本。 |
| [PROJECT-STATUS.md](PROJECT-STATUS.md) | **单文件交接快照**：环境/账号/恢复步骤/审计要点/下一步。 |

## 快速开始

```bash
# 1. 克隆被测系统（与本仓库同级）
git clone https://github.com/macrozheng/mall-swarm.git

# 2. 起基础设施 + 服务（详见 deploy/）
docker compose -f deploy/docker-compose-env.yml up -d
cd mall-swarm && mvn clean install -DskipTests -Ddocker.skip=true && cd ..
powershell -File deploy/run-services.ps1            # 5 服务注册 Nacos

# 3. 跑接口测试 + 报告
cd mall-api-test
mvn test                          # 默认 59 用例（slow 排除）
mvn test -Pslow                   # 含 MQ 真实延迟（约 60s）
allure serve target/allure-results
```

## 文档索引

| 文档 | 用途 |
|---|---|
| [docs/test-system-design.md](mall-api-test/docs/test-system-design.md) | **测试工程体系设计**：大厂维度全景 × 现状 × 目标 + 重点探索(数据隔离/并发/微服务/中间件/效能) + **STAR 叙事库** |
| [docs/test-roadmap.md](mall-api-test/docs/test-roadmap.md) | **后续链路路线图**：去重驱动 + 风险分类 + 7 条样板链路提案 |
| [docs/order-chain-exemplar.md](mall-api-test/docs/order-chain-exemplar.md) | **下单主链路深度样板**：业务流 / oracle 来源 / 缺陷 / 可复现设计 / 怎么跑 |
| [docs/test-conventions.md](mall-api-test/docs/test-conventions.md) | **测试规范**：分层 / 命名 / 断言契约 / 数据策略 / 缺陷协议 / Severity / 门禁 |
| [docs/test-coverage.md](mall-api-test/docs/test-coverage.md) | 覆盖总览（按链路 + 缺陷探针表） |
| [docs/audit.md](mall-api-test/docs/audit.md) | 框架 / 代码 / 设计审计报告 |

## 技术栈

- 被测：Spring Cloud Alibaba 2025、Spring Boot 3.5、Sa-Token、MyBatis、Elasticsearch、RabbitMQ、Redis、MongoDB、Nacos。
- 测试：Java 17、JUnit 5、RestAssured 5、Allure 2、Awaitility、直连 MySQL/Redis 做灰盒校验。

## 关键约定

- 断言**先看 `body.code`**（200/401/403/404/500，与 HTTP 状态解耦），再断 `data`；金额用 `BigDecimal.compareTo`。
- 鉴权为 Sa-Token **双账号**（admin / member 两套 token），头 `Authorization: Bearer <token>`，统一入口走网关 `:8201`。
- 完整规范见 [test-conventions.md](mall-api-test/docs/test-conventions.md)。

## 致谢

被测系统 [macrozheng/mall-swarm](https://github.com/macrozheng/mall-swarm)（Apache License 2.0）。本仓库仅含测试与部署资产，不含其源码。
