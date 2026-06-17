# 测试覆盖总览

当前 **41 个用例：36 通过 + 5 已知缺陷跳过(@KnownDefect)**。`mvn test` 全绿（已知缺陷默认跳过、不阻断门禁）。

## 按业务链路

| 链路 | 用例类 | 覆盖点 |
|---|---|---|
| **#1 下单主链路** | OrderHappyPathTest | 加购→确认单→下单→支付：金额/库存(锁定·扣减·释放)/状态 |
| | OrderNegativeTest | 缺收货地址、库存不足（守门校验文案） |
| | OrderDiscountNegativeTest | 券门槛未达"优惠券不可用"、积分低于单位"积分不可用" |
| | OrderCancelTest | 取消未付款：status=4 + 锁库存复原 |
| | OrderCouponTest | 全场券抵扣(=price-面额) + 取消券回退未使用 |
| | OrderIntegrationTest | 100积分抵1元 + 扣减积分（+ R6 取消退积分缺陷探针） |
| | OrderPromotionTest | 单品/满减/阶梯 三种促销金额（DB 配置算预期） |
| | OrderTimeoutTest | 超时未付款订单自动关闭（时间触发） |
| | OrderLifecycleTest | 全生命周期 0→1→2→3（member 下单/支付/收货 + admin 发货） |
| | CartManagementTest | 购物车 加购→改数量→删除 |
| **#2 认证与 RBAC** | AuthHappyPathTest | admin/member 双账号登录 → 访问受保护接口 |
| | AuthGuardTest | 无/错 token 401、跨账号 401、错误密码 500、受限角色 403 |
| **#3 超时取消** | OrderTimeoutTest / OrderCancelTest | 超时触发 + 逐单回滚 |
| **#4 商品搜索** | SearchChainTest | DB→ES 导入、全量/关键词检索、create 后 Awaitility 轮询可搜 |
| | AdminProductSearchTest | 跨服务：管理员建商品 → ES 可搜（端到端） |
| **#5 优惠券营销** | MemberCouponTest | 领取(/member/coupon/add) + per_limit 重复领取被拒 |
| | OrderCouponTest | 下单核销 + 取消回退 |
| **会员中心** | MemberAddressCrudTest | 收货地址 新增-详情-修改-删除 |
| **商品浏览** | ProductBrowseTest | 首页内容、商品详情、分类树、推荐品牌（公开） |
| **后台管理** | AdminProductManagementTest | 商品批量上下架/新品/推荐（自隔离：建商品→断言 DB→删除） |
| | AdminOrderManagementTest | 管理员批量关单 status→4（+ R8 关单不退锁库存探针） |

## 缺陷探针（@KnownDefect，按"正确行为"断言，默认跳过）

| 编号 | 缺陷 | 用例 | 实测 |
|---|---|---|---|
| R1 | paySuccess 非幂等，重复支付重复扣库存 | OrderDefectProbeTest.paySuccess_should_be_idempotent | 重复支付库存扣 2 次 |
| R2 | paySuccess 无归属校验，可付他人订单 | OrderDefectProbeTest.paySuccess_should_reject_non_owner | windy 成功支付 test 订单 |
| R4 | 并发下单超卖（无原子库存校验） | OrderDefectProbeTest.concurrent_orders_should_not_oversell | 库存2，5并发全成功 |
| R6 | 积分下单取消不退积分(use_integration 未持久化) | OrderIntegrationTest.cancel_should_refund_used_integration | 取消后积分未回退 |
| R8 | 管理员关单不释放 lock_stock（与用户取消/超时不一致 → 库存泄漏） | AdminOrderManagementTest.admin_close_should_release_locked_stock | 关单后 lock_stock 未复原(实测 1→应 0) |

> R3（下单非事务）、R7（空车 clear 返回500，次要）见 [context-pack/06](../../context-pack/06-historical-badcases.md)，暂未做探针。

## 框架能力一览

- 双账号 token 工厂（admin/member）+ Bearer 头；断言先 `body.code` 后 `data`，金额 BigDecimal.compareTo。
- 数据夹具直连 MySQL：SkuStock/Member/Order/Coupon/Promotion/Address + Redis(失效会员缓存)。
- 业务流程层 OrderFlow（加购取 cartId）。
- 异步：Awaitility 轮询（搜索可见性）；并发：ExecutorService（超卖探针）。
- 缺陷协议：@KnownDefect（元注解 @Disabled）默认跳过、修复后移除即转守护。

## 运行

```bash
mvn test                                  # 全部(已知缺陷自动跳过)
mvn -Dtest=OrderDefectProbeTest test      # 仅缺陷探针(会失败=暴露缺陷)
allure serve target/allure-results        # 报告
```
