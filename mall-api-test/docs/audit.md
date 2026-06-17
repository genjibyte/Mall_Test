# 框架 / 代码 / 设计 审计报告

> 审计日期 2026-06-17 · 范围：mall-api-test 框架代码、测试设计、context-pack 设计。
> 方法：通读源码 + 对实环境核查数据状态。结论先行：**专业、可交付的 P0/P1/P2 接口测试框架；主要技术债是测试数据隔离/漂移**。

## 1. 概览

| 指标 | 值 |
|---|---|
| 框架文件 / 行数 | 45 Java / ~2486 行 |
| 分层 | config / core / auth / client / fixture / flow / cases |
| 测试 | 18 类 · 37 方法（33 通过 + 4 @KnownDefect 跳过）|
| 业务链路 | 5 核心链路 + 浏览/会员/购物车 + 跨服务 |
| 提交 | 17 次，全程 mvn test 全绿 |

## 2. 优点（Strengths）

- **设计分层清晰**：client(接口)/flow(编排)/fixture(数据)/core(断言基础设施)/cases，职责单一、复用度高。
- **断言契约正确**：统一 `body.code` 优先、金额 BigDecimal.compareTo，副作用直连 DB 复核——符合 context-pack/05 QG1。
- **证据驱动**：促销/金额预期复刻源码算法从 DB 配置计算，非魔法数；判定口径均经实测取证。
- **缺陷协议成熟**：4 个 @KnownDefect 探针（R1/R2/R4/R6）按"正确行为"断言、验证后默认跳过、不阻断门禁；其中 **R6 为测试中新发现的真实缺陷**。
- **能力完整**：双 token、DB+Redis 夹具、Awaitility 异步、并发超卖探测、跨服务端到端。
- **文档齐全**：context-pack(9 维) + test-analysis-p0 + test-coverage + CI/CD 设计。

## 3. 发现（Findings，按严重度）

### 🟠 H1 · 测试数据隔离不足 / 状态漂移（已部分治理，2026-06-17）
- **现象（实测）**：曾有 4 个 SKU `lock_stock<0`、243 条软删购物车行、test 累计 184 笔订单。
- **根因**：多用例共享 member test + 同一无促销 SKU，靠 before/after 差值断言 + 手动 setStock/setLockStock 还原；HTTP 级测试无法用 DB 事务回滚隔离（SUT 在自身进程提交）；R1/R4 探针造成负库存。
- **治理进展**：
  - ✅ 新增 `DataHygieneFixture` + `DataIntegrityTest`（常驻绿色守卫：断言无负库存/无超锁，漂移再现即门禁失败）+ `DataMaintenanceTest`（手动复位负库存/清软删购物车）。
  - ✅ 已实跑清理：负库存 4→0、软删购物车 243→0。
  - ✅ 新增覆盖（admin 链路）均采用**自隔离**（自建商品/订单→断言→teardown 复位）。
- **剩余建议**：① 为高风险用例分配独立会员/商品（替代共享 test）；② Testcontainers 全新库实现完全隔离 + 开并行；③ 滞留未付款订单的释放+关闭纳入维护（当前仅报告）。

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

### 🟡 M3 · 真实 MQ 延迟路径未覆盖
- 仅同步 `cancelTimeOutOrder` 覆盖超时；RabbitMQ 延迟队列(CancelOrderReceiver, 60s+)未直接测。
- **建议**：加 `@Tag("slow")` 的 Awaitility 用例（normal_order_overtime=1，轮询至 status=4），CI nightly 跑。

### 🟡 M4 · clearCart 吞错
- 因空车 clear 返回 500(R7)，`OrderFlow.clearCart` 静默忽略所有失败，可能掩盖真实错误。
- **建议**：仅忽略空车特定情形，其余仍暴露。

### 🟡 M5 · admin 模块覆盖薄（部分补齐）
- 原：仅 login/info/deliver/product-create。
- **进展（2026-06-17）**：新增 AdminProductManagementTest（批量上下架/新品/推荐，自隔离）+ AdminOrderManagementTest（批量关单 status→4；并发现 **R8** 关单不释放 lock_stock 缺陷探针）。
- 剩余：审核(verifyStatus)、退货审核、批量删除/恢复、SKU 库存维护未测。

### 🟢 L1–L4（低）
- env.properties 含本地凭据入库（低敏感；真实环境应走 CI Secrets）。
- Allure `@Step` 未用，报告步骤可读性可提升。
- 无 `.gitattributes`，提交时 LF→CRLF 警告频繁（建议统一行尾）。
- 部分用例缺独立 @DisplayName 语义化命名规范化空间。

## 4. 覆盖缺口（Gaps）

- 会员：注册(短信验证码流程)、改密、信息。
- 搜索：综合搜索(brandId/category/sort)、推荐、相关筛选(relate)。
- admin：批量上下架/审核/关单/退货审核/SKU 库存维护。
- 缺陷：R3(下单非事务)未做探针；R1/R2/R4/R6 已覆盖。
- 范围外（明确不做）：UI、性能/压测、支付宝真实验签。

## 5. 结论与改进优先级

**总体**：架构与断言质量高，是一套可直接用于回归/CI 的专业框架。最大技术债集中在**测试数据隔离/漂移(H1/H2)**——H1 已部分治理（守卫+维护+自隔离），完全隔离(独立账号/Testcontainers)与并行仍待办。

改进优先级（2026-06-17 更新）：**~~H1 损伤清理~~(完成) → H1 完全隔离/并行 → H2 专用测试账号 → M3 MQ 异步用例 → ~~M5 admin~~(部分完成) → M1/M2 → L\***。
