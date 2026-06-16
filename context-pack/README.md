# mall-swarm 接口自动化测试 — 上下文/约束包（Context Pack）

> 本目录是为「mall-swarm 接口自动化测试框架」项目准备的**上下文与约束包**。
> 面向两类读者：①接手该项目的测试开发/工程师；②协助开发的 AI Agent。
> 目标：任何人/Agent **冷启动**时，读完本包即可获得足够上下文，按统一约束开展工作，不需重新逆向。

## 为什么需要这个包

mall-swarm 是一套 9 模块的微服务电商系统（Spring Cloud Alibaba 2025 / Spring Boot 3.5 / Sa-Token / Java 17）。
直接上手测试会踩三类坑：①鉴权是 Sa-Token **双账号**且响应码与 HTTP 状态**解耦**；②本地部署有多处隐性依赖（Nacos 配置、ES IK 插件、端口冲突）；③下单等核心链路有非事务/非幂等等**已知缺陷**，是测试重点而非"环境问题"。本包把这些固化为可复用上下文。

## 文件清单（阅读顺序）

| # | 文件 | 维度 | 内容 |
|---|---|---|---|
| 0 | [00-goals.md](00-goals.md) | 目标 | 项目要达成什么、成功判据 |
| 1 | [01-scope-boundaries.md](01-scope-boundaries.md) | 边界 | 测什么/不测什么、测试层级、环境边界 |
| 2 | [02-code-context.md](02-code-context.md) | 代码上下文 | 模块图、包约定、响应契约、鉴权机制、入口清单 |
| 3 | [03-business-context.md](03-business-context.md) | 业务上下文 | PMS/OMS/SMS/UMS/CMS 领域、下单/券/积分规则、状态机、业务不变量 |
| 4 | [04-runtime-environment.md](04-runtime-environment.md) | 运行环境 | 已部署环境的端口/账号/启停/重置 |
| 5 | [05-quality-gates.md](05-quality-gates.md) | 质量门禁 | 断言规则、隔离、覆盖目标、DoD、CI 门禁 |
| 6 | [06-historical-badcases.md](06-historical-badcases.md) | 历史 badcase | 源码级缺陷（测试靶点）+ 部署/运行踩坑（含社区 issue） |
| 7 | [07-handover-protocol.md](07-handover-protocol.md) | 交接协议 | 冷启动流程、证据规则、记忆指针、更新协议 |
| 8 | [08-cicd-design.md](08-cicd-design.md) | CI/CD 设计 | 流水线分阶段设计预案（**暂不实施**），环境策略与门禁 |

配套（已存在，非本包但强相关）：
- 部署产物与脚本：[../deploy/](../deploy/)（`docker-compose-env.yml` / `run-services.ps1` / `README.md`）
- 仓库源码：`../mall-swarm/`（**只读，禁止修改**）
- Agent 长期记忆：`~/.claude/projects/E--Mall-Test/memory/`（`mall-swarm-*`、`order-chain-findings`）

## 核心约束（一句话版，详见各文件）

1. **不改 mall-swarm 源码**——测试是黑盒+直连 DB 灰盒，环境层用 OS env 覆盖，不动仓库。
2. **断言先看 `body.code`**（200/401/403/404/500），它与 HTTP 状态解耦；再断 `data`。
3. **统一入口走网关 `:8201`**；带 `Authorization: Bearer <token>`，admin 与 member 是**两套 token**。
4. **一切结论需证据**——源码 `file:line` / 运行输出 / github issue，禁止臆造接口或字段。
5. **金额用 BigDecimal 精确比对**，库存/状态变更需查 DB 复核。

## 维护

本包是"活文档"。当①仓库升级、②部署环境变动、③发现新 badcase 时，按 [07-handover-protocol.md](07-handover-protocol.md) 同步更新对应文件，并在本 README 注明变更。
版本基线：mall-swarm `master`（Spring Cloud 2025 / Boot 3.5 / Sa-Token 1.42 / Java 17），编制于 2026-06。

来源：[mall-swarm GitHub](https://github.com/macrozheng/mall-swarm) · [mall 学习教程](https://www.macrozheng.com/) · 本地源码逐文件研读 + 实际部署验证。
