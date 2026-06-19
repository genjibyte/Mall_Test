# 框架 / 代码 / 设计 审计报告

> 初次审计 2026-06-17；**企业化收口更新 2026-06-19**。范围：mall-api-test 框架代码、测试设计、context-pack 设计。
> 方法：通读源码 + 对实环境核查数据状态。结论先行：**企业级、可交付的接口测试框架**——分层清晰、证据驱动、缺陷可追溯、报告工程化、数据可复现；原最大技术债 H1(数据漂移)已治理(守卫+维护+双隔离)；当前唯一受阻项为搜索链路(ES 7.17.3 撞 WSL cgroup v2，环境性，非代码)。

## 1. 概览

| 指标 | 值 |
|---|---|
| 框架文件 / 行数 | 62 Java / ~3811 行 |
| 分层 | config / core / auth / client / fixture / flow / cases / support |
| 测试 | 27 类 · **60 用例**：默认 59（53 通过 + 6 跳过[5 @KnownDefect + 1 数据维护]）+ 1 `@Tag(slow)` MQ 默认排除 |
| 业务链路 | 5 核心链路 + 浏览/会员/购物车 + 后台管理 + 退货售后 + 搜索筛选 + 跨服务 + 测试基建 |
| 提交 | 34 次（全程 mvn test 全绿；搜索 9 用例当前因 ES cgroup v2 环境受阻，代码已验证、修法见 PROJECT-STATUS §5）|

## 2. 优点（Strengths）

- **设计分层清晰**：client(接口)/flow(编排)/fixture(数据)/core(断言基础设施)/cases，职责单一、复用度高。
- **断言契约正确**：统一 `body.code` 优先、金额 BigDecimal.compareTo，副作用直连 DB 复核——符合 context-pack/05 QG1。
- **证据驱动**：促销/金额预期复刻源码算法从 DB 配置计算，非魔法数；判定口径均经实测取证。
- **缺陷协议成熟**：5 个 @KnownDefect 探针（R1/R2/R4/R6/R8）按"正确行为"断言、验证后默认跳过、不阻断门禁、并 `@Issue` 可追溯；其中 **R6/R8 为测试中新发现的真实缺陷**。
- **能力完整**：双 token、DB+Redis 夹具、Awaitility 异步、并发超卖探测、跨服务端到端、真实 MQ 延迟超时(@slow)。
- **企业级工程化（2026-06-19）**：① Allure 报告地基（environment/categories/Severity/Owner/Story/Issue 可追溯，会话监听器自动注入）；② 测试设计严谨度（折扣边界/等价类 @ParameterizedTest 数据驱动）；③ 可复现（三种数据策略 + 双隔离夹具 + 库存完整性常驻守卫 + 维护回收）。
- **文档齐全**：context-pack(9 维) + 下单链路深度样板(order-chain-exemplar) + 测试规范(test-conventions) + test-coverage + 审计 + CI/CD 设计 + README 工程门面。

## 3. 发现（Findings，按严重度）

### 🟠 H1 · 测试数据隔离不足 / 状态漂移（已部分治理，2026-06-17）
- **现象（实测）**：曾有 4 个 SKU `lock_stock<0`、243 条软删购物车行、test 累计 184 笔订单。
- **根因**：多用例共享 member test + 同一无促销 SKU，靠 before/after 差值断言 + 手动 setStock/setLockStock 还原；HTTP 级测试无法用 DB 事务回滚隔离（SUT 在自身进程提交）；R1/R4 探针造成负库存。
- **治理进展**：
  - ✅ 新增 `DataHygieneFixture` + `DataIntegrityTest`（常驻绿色守卫：断言无负库存/无超锁，漂移再现即门禁失败）+ `DataMaintenanceTest`（手动复位负库存/清软删购物车）。
  - ✅ 已实跑清理：负库存 4→0、软删购物车 243→0。
  - ✅ 新增覆盖（admin 链路）均采用**自隔离**（自建商品/订单→断言→teardown 复位）。
  - ✅ 新增 `IsolatedSkuFixture`（专用无促销大库存 SKU）+ `IsolatedMemberFixture`（克隆 test 哈希建专用会员 isotest-mall，可登录、自带地址）+ `IsolatedOrderFlowTest`：**会员+商品双隔离**下单→支付，库存与订单均不触碰共享种子 test；专用会员订单可由 `DataHygieneFixture.purgeMemberOrders` 整体回收（DataMaintenanceTest 已接入）。
- **剩余建议**：① 既有库存类用例渐进迁移到 Isolated* 夹具（当前仅证明用例使用）；② Testcontainers 全新库实现完全隔离 + 开并行；③ 滞留未付款订单的释放+关闭纳入维护（当前仅报告）。

### 🔴 H2 · 夹具持久修改种子数据且不还原
- **现象（实测）**：windy/zhengsan/lisi/wangwu/lion 5 个会员密码被改为 test 同哈希；productAdmin 密码、member 积分亦被改。
- **影响**：种子数据被污染（幂等但不可逆为原值，因原密码未知）。
- **建议**：在 README/夹具注释明确声明这些是"测试启用账号"；理想做法是用专用测试账号（独立 seed），避免动既有种子。

### 🟡 M1 · 硬编码业务锚点
- product 26(P20)/sku 163/member id/address 4 等散落在用例。`findOrderableNoPromo` 取 id 最小，数据变动会改变选中对象。
- **建议**：集中到 fixture/config 常量 + 前置存在性校验，降低数据耦合。

### 🟡 M2 · 并行执行禁用
- 因共享状态，`junit.jupiter.execution.parallel.enabled=false`。规模上来后串行变慢。
- **建议**：完成 H1 隔离后按资源分组开并行。

### 🟢 M3 · 真实 MQ 延迟路径（已覆盖，2026-06-17）
- ✅ 新增 `OrderTimeoutMqTest`（`@Tag("slow")`，默认排除、`-Pslow` 跑）：降 normal_order_overtime=1 → 真实 TTL队列→DLX→CancelOrderReceiver → 轮询至 status=4 + 校验释放锁库存。实跑约 60s 通过。
- **关键陷阱（已解决）**：RabbitMQ 按消息 TTL 存在**队头阻塞**——既有长 TTL 消息(实测积压 68 条)会拖住新的短 TTL 消息。用例下单前经 `RabbitFixture.purgeQueue` 清空 ttl 队列保证确定性。

### 🟡 M4 · clearCart 吞错
- 因空车 clear 返回 500(R7)，`OrderFlow.clearCart` 静默忽略所有失败，可能掩盖真实错误。
- **建议**：仅忽略空车特定情形，其余仍暴露。

### 🟡 M5 · admin 模块覆盖薄（部分补齐）
- 原：仅 login/info/deliver/product-create。
- **进展（2026-06-17）**：新增 AdminProductManagementTest（批量上下架/新品/推荐/**审核**/**软删恢复**，自隔离）+ AdminOrderManagementTest（批量关单 status→4；并发现 **R8** 关单不释放 lock_stock 缺陷探针）。
- 剩余：~~退货审核~~**已覆盖**(OrderReturnApplyTest)、SKU 库存维护、订单收货人/费用改单未测。

### 🟢 L1–L4（低）
- env.properties 含本地凭据入库（低敏感；真实环境应走 CI Secrets）。
- Allure `@Step` 未用，报告步骤可读性可提升。
- ~~无 `.gitattributes`，提交时 LF→CRLF 警告频繁~~**已加 `.gitattributes` 统一 LF**。
- 部分用例缺独立 @DisplayName 语义化命名规范化空间。
- **observation**：admin `/product/create` 不传 `promotionType` 时落库为 NULL，导致后续购物车促销/下单计算 NPE→HTTP 500（IsolatedSkuFixture 已显式置 0 规避）；属创建入参健壮性缺口，宜后端默认 0。

## 4. 覆盖缺口（Gaps）

- 会员：注册(短信验证码流程)、改密、信息。
- 搜索：~~综合搜索(brandId/category/sort)~~**已覆盖**；推荐(recommend)、相关筛选(relate)未测。
- admin：~~批量上下架/审核/关单/退货审核~~**已覆盖**；SKU 库存维护、订单改单(收货人/费用)未测。
- 缺陷：R3(下单非事务)未做探针；R1/R2/R4/R6/R8 已覆盖。
- 搜索：recommend/relate 未测（且当前 ES 受 cgroup v2 环境阻塞）。
- 范围外（明确不做）：UI、性能/压测、支付宝真实验签。

## 5. 结论与改进优先级

**总体**：已是企业级、可直接用于回归/CI 的框架——分层/断言/证据驱动质量高，叠加报告可追溯、测试设计严谨度(边界/等价类)、数据可复现(双隔离+守卫+维护)。原最大技术债 H1 已实质治理（守卫+维护+会员/商品双隔离已落地，仅剩既有用例渐进迁移 + Testcontainers/并行）。当前唯一受阻为搜索链路的 ES cgroup v2 环境问题(非代码，修法已记)。

改进优先级（2026-06-19 更新）：**~~H1 损伤/隔离~~(已落地) → ~~M3 MQ~~ / ~~M5 admin~~ / ~~搜索筛选~~ / 企业化①②③(均完成) → 搜索 ES 环境修复(.wslconfig cgroup v1) → 既有用例迁移 Isolated*/并行 → CI 落地 → 长尾(会员注册/recommend/relate/R3)**。
