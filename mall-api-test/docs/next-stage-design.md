# 下一阶段设计

> 目标：继续把 `mall-api-test` 从“可运行的接口自动化”推进到“可解释、可复现、可度量、可落地”的质量工程资产。后续工作不以用例数量为目标，而以风险覆盖、可维护性和门禁价值为目标。

## 1. 设计原则

| 原则 | 说明 |
|---|---|
| 风险优先 | 只做能解释业务风险或工程风险的链路，不为了接口数量铺脚本。 |
| 复用优先 | 新增请求进 `client`，数据进 `fixture`，编排进 `flow`，用例只表达意图和断言。 |
| 可复现优先 | 每条会写数据的用例必须有隔离、回滚或清理策略。 |
| 可度量优先 | 默认执行路径必须能产出 `quality-metrics.json`、`quality-summary.md`、`quality-history.jsonl`。 |
| 边界诚实 | 不伪造生产指标，不把受控模拟说成真实线上数据。 |

## 2. 阶段路线

| 阶段 | 优先级 | 目标 | 主要产出 | 完成标准 |
|---|---:|---|---|---|
| S1 质量门禁增强 | P0 | 让每次测试运行可汇总、可追踪、可接 CI | `run-quality-gate.ps1`、`quality-history.jsonl` | **已落地**：每次运行追加一条历史记录，包含 runId、ciBuildId、commit、suite、耗时、结果、KnownDefect 编号 |
| S2 会员注册认证 | P0 | 覆盖 Redis 验证码、注册防重、改密登录态 | `MemberRegistrationAuthTest`、`AuthClient` 扩展、会员清理夹具 | **已落地**：单类可独立运行，写入数据可清理 |
| S3 营销券生命周期 | P1 | 用 admin API 造券，验证限量、时间窗、并发领取 | `AdminCouponClient`、`CouponLifecycleAdminApiTest`、券生命周期用例 | **已落地**：admin API 造券、总量、enable_time 已覆盖；并发领取以 R9 缺陷探针固化 |
| S4 RBAC 配置动态生效 | P1 | 验证授权配置到网关鉴权的闭环 | 角色/资源 client、权限缓存刷新用例 | 能证明 403/200 随配置变化 |
| S5 审计链路收口 | P1 | 串起风险、用例、运行、证据、缺陷、提交 | `audit-chain-design.md`、门禁指标字段、缺陷登记规范 | 新增链路能从风险追到证据和 commit |
| S6 秒杀展示闭环 | P2 | 只验证后台配置与首页展示，不做假下单并发 | `AdminFlashClient`、`FlashPromotionFixture`、首页展示用例 | 当前时间窗内外展示行为可复现 |
| S7 CI 落地 | P2 | 把本地门禁脚本接入流水线 | CI job、构件归档、Allure 报告 | CI 归档质量指标和 Allure results |

## 3. 当前优先级

### P0：已落地

1. **质量门禁增强**
   - 追加 `target/quality-history.jsonl`。
   - 每条历史包含 `runId`、`ciBuildId`、`gitCommit`、`gitBranch`、`runStartedAt`、`runFinishedAt`、`suite`、`testFilter`、`durationSeconds`、`total/passed/failures/errors/skipped`、`knownDefectIds`。
   - 未来 CI 或平台只读这个文件即可做趋势。

2. **会员注册与认证安全**
   - `/sso/getAuthCode` 写 Redis 验证码。
   - `/sso/register` 成功注册。
   - 错误验证码拒绝。
   - 重复用户名/手机号拒绝。
   - `/sso/updatePassword` 后旧密码失效、新密码可登录。

### P1：随后做 / 补强

1. **营销券生命周期**
   - 优先从 admin API 创建券，而不是 DB 直接造券。
   - 已覆盖总量和时间窗。
   - 并发领取不超发已作为 R9 缺陷探针固化。
   - 待补：不同会员限量场景。

2. **RBAC 配置动态生效**
   - 不重复“看到 403”本身。
   - 只测“配置变化 -> 缓存刷新 -> 鉴权结果变化”的闭环。

3. **审计链路收口**
   - 新增链路必须说明 risk、case、data strategy、evidence、result、commit。
   - 缺陷探针必须进入 `06-historical-badcases.md` 和 `test-coverage.md`。
   - 质量门禁历史必须能回答“哪次运行、哪个 CI 构建、哪个提交、哪些用例、哪些跳过、哪些缺陷”。
   - 详细设计见 [audit-chain-design.md](audit-chain-design.md)。

### P2：谨慎做

1. **秒杀展示闭环**
   - 当前 SUT 有后台秒杀配置和首页展示。
   - 当前 SUT 下单促销计算未完整处理 `promotionType=5`。
   - 因此只做“后台配置 -> 首页展示”的真实闭环。
   - 暂不做“秒杀下单并发抢购”，避免假链路。

2. **CI 落地**
   - 先接质量门禁脚本和产物归档。
   - 后续再做全新环境拉起。

## 4. 边界与不做项

| 不做 | 原因 |
|---|---|
| 不为了覆盖率补低价值 CRUD | 纯 CRUD 已有后台管理样板，重复价值低。 |
| 不做假秒杀下单并发 | 被测系统下单链路未闭环支持秒杀价/秒杀库存。 |
| 不把混沌用例放入默认门禁 | 混沌会停止中间件容器，必须独立手动或独立流水线。 |
| 不把 `@KnownDefect` 计作通过 | 它是缺陷资产，默认跳过；修复后再转回归。 |
| 不伪造生产指标 | 无真实流量、生产缺陷库、线上 MTTR，相关指标只能标注模拟或不使用。 |
| 不改 `mall-swarm` 源码 | 本仓库定位是测试资产，被测系统保持外部依赖。 |

## 5. 落地检查清单

新增任一链路必须满足：

1. 请求封装在 `client`。
2. 数据准备/清理封装在 `fixture`。
3. 用例含 `@Epic/@Feature/@Story/@Owner/@Severity`。
4. 先断 `body.code`，再断 `data`。
5. 写入数据有清理策略。
6. 本地能用 `mvn -Dtest=<TestClass> test` 单独运行。
7. `tools/run-quality-gate.ps1 -Test <TestClass>` 能生成质量指标。
8. 能在审计链路中追到风险来源、运行证据、缺陷编号或提交记录。

## 6. 与平台的关系

后续如果接入 `AI-Test-Platform`，不要复制测试逻辑。推荐只接产物：

```text
mall-api-test
  -> target/quality-metrics.json
  -> target/quality-history.jsonl
  -> target/allure-results/
  -> AI-Test-Platform
```

这样测试执行、质量度量和平台展示解耦，后续迁移成本最低。
