# 下单主链路 · 深度样板（Order Placement Chain Exemplar）

> 本文把 mall-swarm 的**下单主链路**测试做成一个**可讲、可复现、可维护**的样板：
> 既是这条链路的测试说明书，也是"如何为一条复杂业务链路设计接口测试"的范式。
> 配套覆盖总览见 [test-coverage.md](test-coverage.md)，框架审计见 [audit.md](audit.md)，
> 源码级事实见 context-pack 与 agent 记忆 `order-chain-findings`。

---

## 1. 为什么单独把它做成样板

下单链路是电商最复杂、最值钱、最容易出 bug 的链路：**金额计算（BigDecimal/促销/券/积分）× 库存并发（锁定/扣减/释放）× 状态机（5 态）× 跨服务（portal/admin）× 异步（MQ 超时）**。
把它讲透，等于讲透了这套框架的全部能力：双账号鉴权、`body.code` 断言契约、DB 灰盒复核、证据驱动 oracle、缺陷协议、数据隔离与可复现。

---

## 2. 业务流程（端到端）

```
会员侧:  /sso/login ──► /cart/add ──► /order/generateConfirmOrder(预览,只读)
            └► /order/generateOrder(下单, status=0) ──► /order/paySuccess(status=1)
                                                          │
管理侧:                          /order/update/delivery(发货, status=2)
                                                          │
会员侧:                          /order/confirmReceiveOrder(收货, status=3)

取消/超时:  /order/cancelUserOrder 或 MQ TTL→DLX→CancelOrderReceiver  ──► status=4
```

- **统一入口**：全部经网关 `:8201`，路径前缀路由（`/mall-portal/...`、`/mall-admin/...`），`StripPrefix=1`。
- **鉴权**：Sa-Token 双账号——portal 用 `StpMemberUtil`、admin 用 `StpUtil`，各自 `Authorization: Bearer <token>`。
- **断言契约**：响应恒为 `{code,message,data}`，`code`（业务码 200/401/403/404/500）**与 HTTP 状态解耦**；业务失败走 `Asserts.fail`→HTTP 200 + `body.code=500`。**永远先断 `body.code`**。

## 3. 库存与金额模型（oracle 的真相来源）

**库存** `pms_sku_stock(stock, lock_stock)`，可用 `realStock = stock − lock_stock`：

| 动作 | stock | lock_stock |
|---|---|---|
| 下单 generateOrder | 不变 | **+qty**（锁定） |
| 支付 paySuccess | **−qty** | −qty（释放） |
| 取消/超时 cancel | 不变 | −qty（释放） |

**状态机** `oms_order.status`：`0 待付款 →(pay) 1 待发货 →(admin 发货) 2 已发货 →(确认收货) 3 已完成`；`0 →(取消/超时) 4 已关闭`。

**金额**（BigDecimal）：`payAmount = totalAmount + freight(恒 0) − promotionAmount − couponAmount − integrationAmount`。
促销分三类（源码 `OmsPromotionServiceImpl`）：单品（price−promotionPrice）、满减（按 SPU 总价分摊 reducePrice）、阶梯（按件数命中折扣）。

## 4. 用例目录（每条都标注 oracle 来源与数据策略）

| 用例 | 意图 | Oracle 来源 | 数据策略 |
|---|---|---|---|
| `OrderHappyPathTest` | 加购→确认单→下单→支付 全链路金额/库存/状态 | `price×qty`（无促销） | 种子SKU + **teardown DB快照还原**（drift-free） |
| `OrderPromotionTest` | 单品/满减/阶梯 应付金额 | `PromotionFixture` 复刻源码算法从 DB 配置算 | 种子促销品 + teardown 取消还原 |
| `OrderCouponTest` | 全场券抵扣 + 取消回退 | `price − 面额` | 自建券 + teardown 删券/取消 |
| `OrderIntegrationTest` | 积分抵扣 + 扣减（+R6 探针） | `useIntegration/use_unit` | 改积分 + 失效缓存 + teardown 还原 |
| `OrderDiscountNegativeTest` | 券门槛/积分门槛 不满足 | 源码文案 | 自建券/改积分 + teardown |
| `OrderNegativeTest` | 缺地址 / 库存不足 守门 | 源码文案（contains） | teardown 还原锁库存 |
| `OrderCancelTest` | 取消未付款→4 + 锁库存复原 | 库存模型 | 种子SKU + 取消即还原 |
| `OrderLifecycleTest` | 0→1→2→3 跨账号（含 admin 发货） | 状态机 | 种子SKU + teardown DB快照还原 |
| `OrderTimeoutTest` | 同步 cancelTimeOutOrder 超时关单 | 状态机 | backdate + 还原 |
| `OrderTimeoutMqTest` `@slow` | **真实** MQ 延迟队列超时（TTL→DLX→消费者） | 状态机 + 释放锁库存 | 清队列 + 改 overtime + teardown 还原 |
| `CartManagementTest` | 加购→改量→删除 | 购物车行 | 自清理 |
| `IsolatedOrderFlowTest` | **完全隔离**下单→支付 | 库存模型 | **专用会员+专用SKU**（零漂移） |
| `OrderDefectProbeTest` | R1/R2/R4 缺陷探针 | "正确行为"断言 | finally 全还原 |
| `AdminOrderManagementTest` | 管理员关单（+R8 探针） | 状态机 + 库存模型 | 自下单 + teardown 还原 |

## 5. Oracle 不是魔法数

样板的核心纪律：**每个预期值都能追溯到源码算法或 DB 配置，绝不写死**。

- 无促销：`payAmount = price × qty`，price 取自被订 SKU。
- 促销：`PromotionFixture` 用与生产相同的 BigDecimal 算法、相同舍入，从 `pms_product_ladder` / `pms_product_full_reduction` 读配置现算 —— 与应用首次即对齐。
- 库存：先 `read()` 快照，再按"下单 +qty / 支付 −qty / 取消释放"断**差值**，不依赖绝对值。
- 文案：取自源码 `OmsPortalOrderServiceImpl` 的 `Asserts.fail("...")`，用 `contains` 规避全角标点差异。

## 6. 缺陷探针（按"正确行为"断言，默认 `@KnownDefect` 跳过、不阻断门禁）

| 编号 | 缺陷 | 探针断言（正确行为） | 实测 |
|---|---|---|---|
| R1 | paySuccess 非幂等 | 重复支付库存只扣一次 | 扣两次 |
| R2 | paySuccess 无归属校验 | 他人不能支付我的订单 | windy 成功支付 test 的单 |
| R4 | 并发下单超卖 | 成功单数 ≤ 可用库存 | 库存2，5并发全成功 |
| R6 | 积分取消不退（use_integration 未持久化） | 取消后积分回退 | 未回退 |
| R8 | 管理员关单不释放锁库存 | 关单后 lock_stock 复原 | 停在锁定值（库存泄漏） |
| R3 | generateOrder 无 @Transactional | （未做探针，难确定性复现） | — |

> 探针先以"会失败=暴露缺陷"验证一次，再加 `@KnownDefect` 默认跳过——既留证据又不污染门禁。

## 7. 可复现设计（本样板的工程内核）

HTTP 级接口测试**无法**用单用例 DB 事务回滚隔离（SUT 在自身进程提交），故采用三层防线：

1. **数据策略矩阵**（每个用例显式选一种，见 §4 末列）：
   - 取消即还原（未支付）→ 最轻；
   - DB 快照 `setStock/setLockStock` 还原（已支付不可回退）→ 旗舰/生命周期用；
   - **完全隔离**（专用会员+SKU）→ 零漂移、可无限重复（`IsolatedOrderFlowTest`）。
2. **库存完整性守卫** `DataIntegrityTest`：常驻绿色，断言"无负锁库存、无超锁"；R1/R4/R8 若造成漂移会令门禁失败 → 把数据损伤暴露为可见信号。
3. **维护与回收** `DataMaintenanceTest`（手动）：复位负库存、清软删购物车、整体回收隔离会员订单。

复用基建：`OrderFlow.placeOrder()` 统一"清车→加购→下单→断言"样板；`IsolatedMemberFixture`/`IsolatedSkuFixture` 提供一次性会员/商品。

## 8. 怎么跑（复现步骤）

```bash
# 1. 环境（隔离端口，详见 PROJECT-STATUS.md §5）
docker compose -f deploy/docker-compose-env.yml up -d
cd mall-swarm && mvn clean install -DskipTests -Ddocker.skip=true && cd ..
powershell -File deploy/run-services.ps1            # 5 服务注册 Nacos

# 2. 跑下单链路
cd mall-api-test
mvn -Dtest='Order*Test,Cart*Test,IsolatedOrderFlowTest' test   # 下单主链路全集
mvn -Dtest=OrderHappyPathTest test                              # 单看旗舰
mvn -Dtest=OrderDefectProbeTest "-Djunit.jupiter.conditions.deactivate=*" test  # 跑缺陷探针(会失败=暴露)
mvn test -Pslow                                                 # 含 MQ 真实延迟(约 60s)
allure serve target/allure-results                             # 报告
```

## 9. 加新下单用例的范式（可维护）

1. 取 SKU：无促销用 `SkuStockFixture.findOrderableNoPromo(qty)`；要零漂移用 `IsolatedSkuFixture.ensure()`。
2. 下单：`long orderId = OrderFlow.placeOrder(token, addressId, sku, qty).dataLong("order","id")`。
3. 断言：先 `body.code`，金额用 `assertAmountEquals`（BigDecimal.compareTo），库存断差值（先 `read()` 快照）。
4. **必须**选一种数据策略并在 `@AfterEach` 兑现（取消还原 / DB 快照还原 / 用隔离数据）。
5. 已知缺陷：按"正确行为"写断言 → 验证失败一次 → 加 `@KnownDefect`。
6. 慢用例（>60s / 真实异步）：打 `@Tag("slow")`，默认排除、`-Pslow` 跑。
