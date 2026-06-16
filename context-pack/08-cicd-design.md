# 08 · CI/CD 设计（仅设计，暂不实施）

> 现阶段**不接 CI/CD**，本文是设计预案，便于将来一键落地。落地门禁见 [05-quality-gates.md](05-quality-gates.md) QG8。
> 本文内的 YAML 为**设计草案**，不放入 `.github/workflows/`（放入即激活），保持当前"不实施"。

## 1. 现状与为何暂缓

- 当前为本地手工环境：6 容器基础设施 + 5 个 JVM 服务，端口因本机其它栈做了隔离重映射。
- 环境**重型**：ES/Nacos/RabbitMQ + 5 服务，冷启动到可测约数分钟；且 mall-swarm 需先构建。
- 用例尚在 P0 阶段，优先把覆盖做扎实再自动化，避免过早固化不稳定流程。

## 2. 目标

PR/定时触发 → **自动拉起被测环境 → 跑接口用例 → 出 Allure 报告 → 按 P0 结果判定门禁**，全程可重复、可观测。

## 3. 流水线总览

```
checkout → 准备环境 → 构建&起服务 → 健康门禁 → 跑测试 → 报告 → 质量门禁 → 清理
  │           │            │            │          │        │         │         │
 本仓库     infra(compose) mvn build   Nacos 5    mvn test Allure   P0全绿?   compose
 +clone     +SQL/Nacos    -Ddocker     服务健康   (env 注入)report  否则失败   down -v
 mall-swarm +IK/importAll  .skip       +登录冒烟
```

## 4. 阶段详解

| 阶段 | 动作 | 失败即停 |
|---|---|---|
| Checkout | 拉本仓库；`git clone` mall-swarm（被测，未纳入本仓库） | 是 |
| 准备基础设施 | `docker compose -f deploy/docker-compose-env.yml up -d`；等待 MySQL/Nacos/ES 健康 | 是 |
| 数据与配置 | 导 `mall.sql`；推 `config/*` 到 Nacos；装 ES IK 插件；`importAll` 商品 | 是 |
| 构建与起服务 | `mvn -f mall-swarm install -DskipTests -Ddocker.skip=true`；启动 5 服务（CI 用默认端口即可，运行器隔离） | 是 |
| 健康门禁 | 轮询 Nacos 注册 5 服务健康 + 网关登录冒烟 `code:200` | 是（不健康不进测试） |
| 跑测试 | `mvn -f mall-api-test test`，用 env 注入端点（见 §7） | 否（结果交门禁判定） |
| 报告 | 收集 `target/allure-results` → 生成 Allure 报告 → 作为构件发布 | 否 |
| 质量门禁 | P0 全绿则通过；`@KnownDefect` 不阻断但单列；flaky 重试仍失败则红 | 是 |
| 清理 | `docker compose down -v`；停服务 | 否 |

## 5. 运行器要求

- 可用 **Docker**（起基础设施）；**JDK 17** + **Maven**；足够内存（ES 1G + 5 服务 ~3G + 基础设施）。
- Linux 运行器：服务可用 `nohup java -jar ... &` 或容器化；端口在隔离运行器用默认值即可（无需本机的重映射）。

## 6. 环境策略（取舍）

| 方案 | 优点 | 缺点 | 建议 |
|---|---|---|---|
| **Ephemeral 全新环境/每次** | 干净、可重复、隔离 | 慢（分钟级冷启动） | **定时(nightly)** 用 |
| 长驻共享测试环境 | 快 | 有状态、易漂移、需维护 | PR 快速冒烟用（仅 P0 子集） |
| Testcontainers(基础设施)+服务jar | 与用例同生命周期、可控 | 服务编排仍复杂 | 中期演进方向 |

推荐分级：**PR** 触发 → 长驻环境跑 P0 冒烟（分钟内）；**Nightly** → ephemeral 全量。

## 7. 配置与密钥

- 端点全部经 env 注入（框架已支持覆盖）：`GATEWAY_BASE_URL`、`DB_URL`、`DB_USERNAME/PASSWORD`、`MEMBER_*`、`ADMIN_*`。
- 本地 demo 凭据（root/root、macro123）仅用于本地/CI 隔离环境；**真实环境**改用 CI Secrets 注入，禁止入库。
- Nacos/RabbitMQ 凭据同理走配置，不硬编码。

## 8. 触发策略

- **PR**：构建本测试工程 + 对长驻环境跑 P0（快速反馈）。
- **Nightly**：ephemeral 全量（P0+P1+缺陷探针），出完整 Allure 报告。
- **手动**：可指定标签子集（如只跑下单链路）。

## 9. 门禁判定（对齐 QG8）

- 通过：P0 全绿 + 无 flaky + 报告生成。
- `@KnownDefect`（R1 幂等/R2 越权支付/R4 超卖等，见 [06](06-historical-badcases.md)）：不阻断，报告单列；修复后转常驻守护。
- 失败：贴失败用例的 `code/message/data` 与关键 DB 状态，附 Allure 链接。

## 10. 风险与取舍

- 冷启动慢 → PR 不跑全量；ES 内存 → 运行器规格保证；IK 插件未挂卷 → CI 每次重装（脚本已就绪）。
- 数据漂移（lock_stock 等）→ 用例自带前置/差值断言；ephemeral 方案天然规避。
- 端口：CI 隔离运行器用默认端口；本机重映射仅为本地共存，勿混用。

## 11. 示例草案（GitHub Actions，**未启用**，仅供落地参考）

```yaml
# 设计草案：将来落地时放到 .github/workflows/p0.yml 才会生效；现在不放。
name: mall-api-p0 (draft)
on: { workflow_dispatch: {} }   # 落地后可加 pull_request / schedule
jobs:
  p0:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - name: Clone SUT
        run: git clone --depth 1 https://github.com/macrozheng/mall-swarm.git
      - name: Up infra
        run: docker compose -f deploy/docker-compose-env.yml up -d
      - name: Prepare data/config
        run: ./deploy/ci/prepare-env.sh    # 导SQL+推Nacos+装IK+importAll（待补）
      - name: Build & start services
        run: ./deploy/ci/start-services.sh  # mvn build + 起服务 + 健康门禁（待补）
      - name: Run P0
        env: { GATEWAY_BASE_URL: http://localhost:8201, DB_URL: 'jdbc:mysql://localhost:3306/mall?useSSL=false&serverTimezone=Asia/Shanghai' }
        run: mvn -f mall-api-test test
      - name: Allure report
        if: always()
        uses: simple-elf/allure-report-action@v1
        with: { allure_results: mall-api-test/target/allure-results }
      - name: Teardown
        if: always()
        run: docker compose -f deploy/docker-compose-env.yml down -v
```

> 落地前需补：`deploy/ci/prepare-env.sh`、`start-services.sh`（把当前手工步骤脚本化为 Linux 版）。
