# 07 · 交接协议（Handover Protocol）

> 接手人/Agent 在**冷启动**或**续作**时遵循。目的：少逆向、不臆造、可持续。

## 冷启动清单（开工前按序）

1. 读本包：[README](README.md) → [00 目标](00-goals.md) → [01 边界](01-scope-boundaries.md) → [02 代码](02-code-context.md) → [03 业务](03-business-context.md) → [04 运行环境](04-runtime-environment.md) → [05 门禁](05-quality-gates.md) → [06 badcase](06-historical-badcases.md)。
2. 起环境并自检：按 [04](04-runtime-environment.md) 启动基础设施+服务，跑健康自检（Nacos 5 服务健康 + 登录冒烟 `code:200`）。
3. 确认数据：ES 已 `importAll`、IK 已装、种子账号可登录。
4. 再动手写代码/用例。

## 证据规则（不可破）

- **任何关于接口/字段/规则的结论，必须有证据**：源码 `模块/路径:行`、运行输出、或 github issue。**禁止凭记忆臆造**端点、参数名、字段、返回结构。
- 记忆/本包里提到的文件名、行号、字段，使用前**复核仓库当前是否仍存在**（仓库可能升级）。
- 报告结果**忠实**：测试失败就贴 `code/message/data` 与 DB 实际状态；跳过的步骤如实说明；不"粉饰绿"。

## 硬约束（重申）

1. **不改 `../mall-swarm/` 源码与配置**。环境差异用 OS env / Nacos 配置在运行期覆盖。
2. 测试代码与产物放仓库**外**（`E:\Mall_Test\` 下独立目录，建议 `tests/` 或 `mall-api-test/`）。
3. 改共享状态（DB 配置/库存/券）→ **用后复原**或用独立数据（见 [05 QG2](05-quality-gates.md)）。
4. 断言**先 `body.code` 后 `data`**；金额 BigDecimal；副作用查库复核（[05 QG1](05-quality-gates.md)）。

## 记忆指针（Agent 长期记忆）

`~/.claude/projects/E--Mall-Test/memory/`：
- `mall-swarm-test-framework` — 项目目标 + 鉴权/契约关键约束
- `mall-swarm-local-deployment` — 部署环境、端口、账号、启停、坑
- `order-chain-findings` — 下单链路库存/金额/缺陷/数据锚点
- 索引 `MEMORY.md`

本包与记忆**互为副本**：本包是给人看的完整文档，记忆是给 Agent 的精炼事实。两者更新需同步。

## 交付物约定

| 产物 | 位置 |
|---|---|
| 上下文/约束包（本包） | `E:\Mall_Test\context-pack\` |
| 部署脚本/编排 | `E:\Mall_Test\deploy\` |
| 被测仓库（只读） | `E:\Mall_Test\mall-swarm\` |
| 测试框架代码（后续） | `E:\Mall_Test\<test-project>\`（独立，不入 mall-swarm） |

## 续作 / 多 Agent 协作

- 续作前先 `TaskList` 看进度；认领任务设 `in_progress`，完成设 `completed`，阻塞则新建任务描述阻塞点。
- 跨会话续作：先读记忆 + 本包，再跑环境自检确认现状（不要假设上次状态仍在）。
- 重大决策（技术选型、范围变更）需与负责人确认后再落，并回写本包对应文件。

## 更新协议（保持"活文档"）

触发更新的情形与动作：
| 触发 | 更新 |
|---|---|
| mall-swarm 升级/改接口 | 复核并更新 [02](02-code-context.md)/[03](03-business-context.md)，标注新基线版本 |
| 部署环境变动（端口/组件） | 更新 [04](04-runtime-environment.md) 与 `deploy/` |
| 发现新缺陷/新坑 | 追加 [06](06-historical-badcases.md)，必要时加用例 |
| 质量标准调整 | 更新 [05](05-quality-gates.md) |
| 任一更新 | 在 [README](README.md) 维护段注明变更与日期 |

## 沟通基线

- 用中文交流（与负责人一致）；结论给推荐项而非罗列所有选项。
- 不确定就先查证（源码/运行/web），查不到再问；问题要具体、可决策。
- 破坏性或对外操作（删数据卷、动其它 QA 栈、改共享配置）先确认。
