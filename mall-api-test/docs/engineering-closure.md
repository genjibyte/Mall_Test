# 工程收口：能解释、能复现、能度量、能落地

> 目标不是继续堆用例，而是把现有框架从“能跑”收束成可交接、可审计、可重复执行、可进入门禁的工程资产。

## 1. 收口总览

| 维度 | 现在要回答的问题 | 已有资产 | 收口产物 |
|---|---|---|---|
| 能解释 | 这套测试为什么这么设计？每条链路测什么风险？ | `test-system-design.md`、`test-coverage.md`、`order-chain-exemplar.md` | 本文的四维索引 + STAR 讲法 |
| 能复现 | 换一台机器/换一个人，怎么恢复环境并稳定跑？ | `deploy/`、`PROJECT-STATUS.md`、数据守卫/维护用例 | 固定恢复路径 + 运行前后检查清单 |
| 能度量 | 不是“感觉全绿”，而是能输出什么指标？ | Surefire XML、Allure results、`@KnownDefect` 协议 | `tools/run-quality-gate.ps1` 生成 JSON/Markdown 指标 |
| 能落地 | 本地、交接、未来 CI 怎么接？ | `context-pack/08-cicd-design.md`、Maven profiles | 质量门禁命令 + 分级执行策略 |

## 2. 能解释

解释这套框架时，不从工具栈开始，而从工程问题开始：

| 工程问题 | 解释口径 | 对应证据 |
|---|---|---|
| HTTP 级接口测试无法事务回滚，如何避免数据漂移？ | 三种数据策略：取消还原、DB 快照还原、完全隔离；再用 `DataIntegrityTest` 常驻守卫。 | `test-conventions.md` §4、`audit.md` H1 |
| 金额/促销断言为什么可信？ | 预期值从 DB 配置与源码算法现算，金额用 `BigDecimal.compareTo`，不写魔法数。 | `order-chain-exemplar.md`、`PromotionFixture` |
| 真实缺陷为什么不让门禁一直红？ | `@KnownDefect` 按正确行为断言，验证后默认跳过；修复后删注解转回归。 | `KnownDefect.java`、`test-coverage.md` 缺陷探针 |
| 慢用例为什么不影响日常反馈？ | `@Tag("slow")` 默认排除，`-Pslow` 夜间/全量执行。 | `pom.xml` profile、`OrderTimeoutMqTest` |
| 中间件故障如何说明？ | 用 `@Disabled` 混沌用例手动注入，避免日常门禁破坏环境。 | `MiddlewareResilienceTest` |

对外讲述建议使用 5 个 STAR：

1. 数据漂移治理：共享态、负库存、软删购物车累积如何被守卫和维护工具收住。
2. 环境可复现：WSL cgroup v2 导致 ES 崩溃，如何 pin ES/IK 版本并沉淀 SOP。
3. 真实异步链路：RabbitMQ TTL + DLX 超时取消，如何处理队头阻塞并隔离到 slow。
4. 缺陷协议：R1/R2/R4/R6/R8 如何从一次性发现变成可追溯资产。
5. 证据驱动 oracle：促销/金额断言如何从源码和 DB 配置推导。

## 3. 能复现

### 3.1 环境恢复路径

```powershell
# 1. 起基础设施
docker compose -f deploy/docker-compose-env.yml up -d

# 2. 构建并启动被测服务
cd mall-swarm
mvn clean install -DskipTests -Ddocker.skip=true
cd ..
powershell -File deploy/run-services.ps1

# 3. 健康检查
curl http://localhost:18848/nacos/v1/ns/catalog/services

# 4. 运行质量门禁
cd mall-api-test
powershell -ExecutionPolicy Bypass -File tools/run-quality-gate.ps1
```

### 3.2 运行前检查

| 检查项 | 期望 |
|---|---|
| 网关 | `http://localhost:8201` 可访问 |
| Nacos | 5 个服务已注册：gateway/auth/admin/portal/search |
| MySQL | `localhost:23306` 可连接，库名 `mall` |
| Redis | `localhost:16379` 可连接 |
| ES | `localhost:19200` 可连接且 IK 插件版本与 ES 一致 |
| RabbitMQ | `localhost:15673` 管理端可访问 |

### 3.3 运行后检查

| 检查项 | 期望 |
|---|---|
| Maven exit code | `0` |
| Surefire failures/errors | `0` |
| `DataIntegrityTest` | 通过，无负锁库存、无超锁 |
| Allure results | `target/allure-results` 有结果 |
| 度量文件 | `target/quality-metrics.json`、`target/quality-summary.md` 已生成 |

若数据漂移，先手动运行维护用例：

```powershell
mvn -Dtest=DataMaintenanceTest "-Djunit.jupiter.conditions.deactivate=*" test
```

## 4. 能度量

统一入口：

```powershell
cd mall-api-test
powershell -ExecutionPolicy Bypass -File tools/run-quality-gate.ps1
```

输出：

| 文件 | 用途 |
|---|---|
| `target/quality-metrics.json` | 机器可读指标，后续 CI/看板可直接消费 |
| `target/quality-summary.md` | 人可读摘要，可贴到交接、PR、日报 |
| `target/surefire-reports/` | JUnit 原始报告 |
| `target/allure-results/` | Allure 原始报告 |

核心指标口径：

| 指标 | 口径 |
|---|---|
| total | Surefire XML 中测试用例总数 |
| passed | `total - failures - errors - skipped` |
| failures/errors | JUnit 断言失败/运行错误 |
| skipped | `@Disabled`、已知缺陷、维护/混沌等跳过 |
| knownDefectSkipped | skipped 中 message 含 `KnownDefect` 的数量 |
| durationSeconds | 脚本端到端执行耗时 |
| mavenExitCode | Maven 进程退出码，门禁最终依据之一 |

门禁建议：

| 场景 | 命令 | 判定 |
|---|---|---|
| 本地日常/PR 快线 | `tools/run-quality-gate.ps1` | Maven exit code 为 0，failures/errors 为 0 |
| 夜间全量 | `tools/run-quality-gate.ps1 -Suite slow` | 包含 slow，允许耗时更长，仍要求 failures/errors 为 0 |
| 单类定位 | `tools/run-quality-gate.ps1 -Test OrderHappyPathTest` | 只看目标类是否通过 |
| 仅汇总已有结果 | `tools/run-quality-gate.ps1 -SkipRun` | 不跑 Maven，只解析现有 Surefire 报告 |

## 5. 能落地

### 5.1 三层落地方式

| 层级 | 适用 | 动作 |
|---|---|---|
| 本地交付 | 个人开发、面试演示、手工回归 | 运行 `tools/run-quality-gate.ps1`，打开 `quality-summary.md` 和 Allure |
| 团队门禁 | 共享测试环境上的 PR 快线 | CI 调用同一脚本，收集 `target/quality-*` 与 Allure |
| 夜间全量 | 慢链路、MQ、缺陷探针抽检 | `-Suite slow` + Allure history 趋势 |

### 5.2 CI 接入最小步骤

1. 准备 JDK 17、Maven、Docker。
2. 拉起 `deploy/docker-compose-env.yml`。
3. 构建并启动 `mall-swarm` 5 个服务。
4. 健康检查通过后进入 `mall-api-test`。
5. 执行：

```powershell
powershell -ExecutionPolicy Bypass -File tools/run-quality-gate.ps1
```

6. 归档：

```text
mall-api-test/target/quality-metrics.json
mall-api-test/target/quality-summary.md
mall-api-test/target/surefire-reports/
mall-api-test/target/allure-results/
```

### 5.3 不做的事

- 不把 `@KnownDefect` 当成通过用例；它是缺陷资产，默认跳过。
- 不把混沌用例放入默认门禁；它会停止容器，必须手动或独立流水线执行。
- 不伪造生产指标；逃逸率、ROI、线上 MTTR 等没有真实生产资产支撑，只能标注为模拟或不使用。
- 不为了“秒杀并发”写假链路；当前 SUT 的下单促销计算未闭环支持 `promotionType=5`，应先做后台配置和首页展示闭环。

## 6. 当前完成定义

这次收口后的完成标准：

- [x] 有一个总入口解释“四个能”的资产关系。
- [x] 有固定的本地恢复和质量门禁命令。
- [x] 有机器可读 `quality-metrics.json`。
- [x] 有人可读 `quality-summary.md`。
- [x] README 能指向收口文档和门禁脚本。
- [ ] CI 真正启用：仍按 `context-pack/08-cicd-design.md` 作为下一阶段，不在本次强行激活。
