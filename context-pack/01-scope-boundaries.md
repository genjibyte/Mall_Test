# 01 · 边界（Scope & Boundaries）

## 测试层级

- **主**：黑盒接口测试，统一经网关 `http://localhost:8201/<service-prefix>/...`，断言 HTTP 响应与统一响应体 `{code,message,data}`。
- **辅**：灰盒——直连 MySQL（`localhost:23306`，库 `mall`）做**前置数据准备**与**后置状态校验**（库存 `pms_sku_stock`、订单 `oms_order`、券 `sms_coupon_history`、积分 `ums_member`）。必要时读 Redis/RabbitMQ/ES 旁路验证。
- **不**直接调用各服务私网端口绕过网关（除非专门测试服务自身；默认走网关以贴近真实链路与鉴权）。

## 在范围内（In Scope）

| 类别 | 内容 |
|---|---|
| 业务链路 | 下单主链路、认证与 RBAC、订单超时自动取消、商品上架→ES 搜索、优惠券营销（详见 [03-business-context.md](03-business-context.md)） |
| 接口类型 | mall-portal（会员侧）、mall-admin（管理侧）、mall-search（搜索）、mall-auth（认证）经网关暴露的 REST 接口 |
| 用例类型 | 正向(happy path)、边界、负向、鉴权/越权、幂等、并发/超卖、最终一致（异步） |
| 校验对象 | 响应 `code`/`message`/`data`、DB 状态变更、金额计算、状态机流转、回滚正确性 |

## 不在范围内（Out of Scope）

| 不做 | 原因 |
|---|---|
| 前端 Vue（mall-admin-web / mall-app-web）UI 自动化 | 本项目是接口层；前端单独立项 |
| mall-swarm 源码单元测试 | 黑盒视角；且**不修改源码** |
| 性能 / 压测 / 稳定性长跑 | 另立专项（可复用本框架的数据准备） |
| mall-monitor、mall-demo | 监控/教学模块，无业务价值（demo 仅 Feign 演示） |
| MinIO / 阿里云 OSS / 支付宝 真实集成 | 本地未部署/占位配置；文件上传与真实支付接口默认跳过或打桩 |
| 真实第三方回调（支付宝 notify 验签） | 占位密钥，无法真实验签；用 `paySuccess` 内部回调模拟支付完成 |

## 环境边界

- 仅针对**本地已部署环境**（见 [04-runtime-environment.md](04-runtime-environment.md)），非生产、非作者演示站。
- 基础设施端口为**隔离重映射**端口（MySQL 23306 / Redis 16379 / Nacos 18848 / RabbitMQ 5673 / ES 19200 / Mongo 27018），因本机有其它 QA 栈占用默认端口。
- mall-admin 服务端口重映射为 **18080**（8080 被占）；网关入口仍是标准 `8201`。

## 硬约束（Constraints）

1. **禁止修改 `../mall-swarm/` 源码**（包括配置文件）。环境差异一律用 OS 环境变量/Nacos 配置在运行期覆盖。
2. 测试代码与产物放在 mall-swarm 仓库**之外**（如 `E:\Mall_Test\` 下独立目录），不污染被测仓库。
3. 改动共享状态（如临时改 `oms_order_setting.normal_order_overtime` 跑超时用例）必须**用后复原**或在独立数据上进行。
4. 不依赖某次手工数据；用例需自带可重复的数据准备。

## 假设（Assumptions）

- 测试运行时，6 个基础设施容器 + 5 个服务均已启动且注册到 Nacos（启动方式见运行环境文件）。
- 种子数据来自 `mall.sql`（admin/macro123、test/123456 等账号存在）。
- ES 已装 IK 插件且已 `importAll`（否则搜索链路用例需先触发导入）。
