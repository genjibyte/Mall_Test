# 测试规范（Test Conventions）· 企业级标准

> 这套规范定义"在本工程里如何写一条接口测试"。新增任何用例都应遵循它。
> 配套：下单链路样板 [order-chain-exemplar.md](order-chain-exemplar.md)、覆盖总览 [test-coverage.md](test-coverage.md)、审计 [audit.md](audit.md)。

## 1. 分层架构（职责单一、由内向外依赖）

| 层 | 包 | 职责 |
|---|---|---|
| 配置 | `config` | 环境配置加载 + 环境变量覆盖（`TestConfig`）|
| 核心 | `core` | RestAssured 封装、统一响应模型、断言契约、`@KnownDefect`|
| 认证 | `auth` | 双账号 token 工厂（member/admin，带缓存）|
| 客户端 | `client` | 一个接口域一个 Client，只发请求、不含断言/编排 |
| 流程 | `flow` | 跨接口编排（如 `OrderFlow.placeOrder`），复用、去重 |
| 夹具 | `fixture` | 直连 MySQL/Redis 的数据准备与**副作用复核** + 隔离/卫生 |
| 用例 | `cases` | 只表达"意图 + oracle + 断言"，数据与编排下沉到 fixture/flow |
| 支撑 | `support` | 报告/监听器等横切基建 |

**铁律**：用例不写 SQL、不拼请求细节；这些属于 fixture/client。用例读起来应像一段业务叙述。

## 2. 命名与组织

- 测试类：`<域><场景>Test`（`OrderHappyPathTest`、`AdminOrderManagementTest`）。
- 测试方法：`snake_case` 表达断言（`cancel_unpaid_order_restores_lock_stock`）；JUnit `ReplaceUnderscores` 生成可读名。
- `@DisplayName`：一句中文，**含预期**（"取消未付款订单 状态置关闭 锁定库存复原"）。
- Allure 维度：`@Epic`(链路) → `@Feature`(子域) → `@Story`(场景)。

## 3. 断言契约（QG1，不可违反）

1. **先断 `body.code`**（业务码 200/401/403/404/500，与 HTTP 解耦），再断 `data`。
2. 金额一律 `BigDecimal.compareTo`（`assertAmountEquals`），忽略 scale。
3. 副作用（库存/订单/券/积分）**直连 DB 复核**，断**差值**而非绝对值（先 `read()` 快照）。
4. 失败文案用 `contains` 片段匹配，规避全角标点。

## 4. 数据策略（可复现，三选一并在 teardown 兑现）

| 策略 | 适用 | 做法 |
|---|---|---|
| 取消还原 | 未支付即结束的用例 | teardown `cancelUserOrder` 释放锁库存 |
| DB 快照还原 | 已支付/不可回退 | `read()` 快照 → teardown `setStock/setLockStock` |
| **完全隔离** | 库存敏感、追求零漂移 | `IsolatedSkuFixture`+`IsolatedMemberFixture`（专用商品+会员） |

守卫与回收：`DataIntegrityTest`（常驻绿，断言无负库存/超锁）；`DataMaintenanceTest`（手动，复位负库存/清软删购物车/回收隔离会员订单）。

## 5. 缺陷协议（可追溯）

- 已知缺陷按**"正确行为"**写断言 → 跑一次"会失败=暴露缺陷"留证 → 加 `@KnownDefect`（元注解 `@Disabled`）默认跳过、不阻断门禁。
- 同时加 `@Issue("Rn")`，报告内链接到 [缺陷目录](order-chain-exemplar.md)（test ↔ defect 可追溯）。
- 修复后删 `@KnownDefect` 即转守护用例。

## 6. Severity 分级（@Severity）

| 级别 | 标准 | 例 |
|---|---|---|
| `BLOCKER` | 核心交易主路径，挂了等于不可用 | 下单→支付、全生命周期 |
| `CRITICAL` | 金额/库存/资金安全、缺陷探针 | 促销/券/积分金额、取消回滚、R1–R8 |
| `NORMAL` | 守门校验、管理操作、CRUD | 缺地址/缺库存负例、收货地址 |
| `MINOR` | 浏览/只读/演示 | 首页/详情、隔离演示 |

类级标注 `@Owner` + `@Severity`；`@Story` 描述场景。

## 7. 标签与门禁

- 默认 `mvn test`：跑全部**快速**用例并全绿；`@KnownDefect`/`@Tag("maintenance")` 默认跳过、不阻断。
- `@Tag("slow")`（真实 MQ 延迟等 >60s）：默认 `<excludedGroups>slow` 排除，nightly 用 `mvn test -Pslow`。
- 门禁判定看 Allure 报告：产品缺陷=失败必须红；已知缺陷=skipped 不计失败。

## 8. 报告（Allure 工程化）

- `AllureEnvironmentListener` 自动写 `environment.properties`（被测环境面板）+ `categories.json`（失败分类）。
- HTTP 请求/响应由 `AllureRestAssured` 过滤器抓取为附件。
- 生成：`mvn test` → `allure serve target/allure-results`。

## 9. 加一条新用例的检查清单

1. [ ] 选层：请求进 client、编排进 flow、数据进 fixture、断言留 case。
2. [ ] `@Epic/@Feature/@Story/@Owner/@Severity`；方法 `@DisplayName` 含预期。
3. [ ] oracle 可追溯（源码算法或 DB 配置算得，**非魔法数**）。
4. [ ] 先断 `body.code`，金额 `compareTo`，副作用查库断差值。
5. [ ] 选一种数据策略并在 `@AfterEach` 兑现（或用隔离夹具）。
6. [ ] 已知缺陷：`@KnownDefect` + `@Issue`；慢用例：`@Tag("slow")`。
7. [ ] 本地 `mvn -Dtest=<NewTest> test` 绿；不破坏 `DataIntegrityTest`。
