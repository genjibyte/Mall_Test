# mall-api-test · mall-swarm 接口自动化测试框架（骨架）

Java + JUnit5 + RestAssured + Allure，针对本地已部署的 mall-swarm（见 [../deploy](../deploy)）做接口自动化。
设计约束见 [../context-pack](../context-pack)（目标/边界/契约/质量门禁/badcase/交接）。

## 分层结构（context-pack/05 QG6）

```
src/test/java/com/mall/test/
├── config/   TestConfig            # 环境配置(env.properties + 环境变量覆盖)
├── core/     ApiResponse           # 统一响应体 {code,message,data} 封装
│             ResultCode            # 业务码 200/401/403/404/500
│             RestClient            # RestAssured 封装(网关 baseUri + Bearer + Allure 抓取)
│             ApiAssertions         # 断言助手：先 body.code 后 data；金额 BigDecimal
├── auth/     TokenFactory          # 双账号 token(member/admin) 缓存
├── client/   AuthClient            # 登录(admin JSON / member form)
│             CartClient            # /cart/add /list/promotion /clear
│             OrderClient           # /order/generateConfirmOrder /generateOrder /paySuccess /detail
├── fixture/  Db                    # 直连 MySQL(灰盒)：数据准备 + 副作用复核
│             SkuStockFixture       # 找可下单 SKU、读 stock/lock_stock
│             MemberFixture         # 会员 id / 默认地址 / 积分
│             OrderFixture          # 订单状态 / 应付金额
└── cases/    order/OrderHappyPathTest   # P0 下单主链路用例
```

四层：接口客户端层(client) / 数据夹具层(fixture) / 断言与基础设施(core) / 用例层(cases)。

## 前置

1. 起环境：基础设施 + 5 服务已启动并注册（见 [../deploy/README.md](../deploy/README.md)），网关 `:8201` 可访问，MySQL `:23306` 可连。
2. JDK 17 + Maven。

## 运行

```bash
mvn test                      # 跑全部用例
mvn -Dtest=OrderHappyPathTest test   # 跑单个
# 可用环境变量覆盖配置（无需改文件）：
#   GATEWAY_BASE_URL=http://localhost:8201  DB_URL=jdbc:mysql://localhost:23306/mall?...  等
```

Allure 报告：结果在 `target/allure-results`，`allure serve target/allure-results` 查看。

## P0 用例覆盖（OrderHappyPathTest）

会员 加购 → 确认单 → 下单 → 支付，断言：
- 金额：确认单/订单 `payAmount == price × qty`（无促销/券/积分，精确可预期）。
- 库存：下单 `lock_stock += qty` 且真实 `stock` 不变；支付 `stock -= qty` 且 `lock_stock` 复原（直连 DB 差值断言）。
- 状态：下单 `status=0`，支付后 `status=1`。

为可重复，用例每次动态选无促销可下单 SKU 并用 before/after 差值断言，不依赖固定数据。

## 关键约定（务必遵守）

- **先断 `body.code`**（200/401/403/404/500），与 HTTP 状态解耦；业务失败常见 HTTP 200 + code 500。
- 金额一律 `BigDecimal.compareTo`，禁止 double/equals。
- 有副作用的接口必须查库复核（库存/订单/券/积分）。
- admin 与 member 是**两套 token**，互不通用。
- 不修改 mall-swarm 源码；本工程独立于被测仓库。

## 下一步（按 context-pack 优先级扩展）

- P0 鉴权：无 token→401、跨会员越权→拒绝。
- P1：库存不足/缺地址/券与积分边界、金额组合（单品/阶梯/满减）、取消回滚（券/积分/库存）。
- P2 缺陷探针（标 `@KnownDefect`）：R1 `paySuccess` 非幂等、R2 越权支付、R4 并发超卖（见 context-pack/06）。
- 异步：订单超时自动取消（改 `oms_order_setting.normal_order_overtime`→1 + Awaitility 轮询）。
