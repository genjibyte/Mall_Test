# 03 · 业务上下文（Business Context）

> 业务规则均来自源码研读 + 实库核实。域前缀含义经[官方教程](https://www.macrozheng.com/)与 [mall#232](https://github.com/macrozheng/mall/issues/232) 印证。

## 业务域（表/类前缀）

| 前缀 | 域 | 含义 | 代表对象 |
|---|---|---|---|
| **PMS** | Product | 商品：分类/属性/SKU/价格/促销 | pms_product, pms_sku_stock, pms_product_ladder, pms_product_full_reduction |
| **OMS** | Order | 订单：购物车/确认单/订单/退货/设置 | oms_cart_item, oms_order, oms_order_item, oms_order_setting |
| **SMS** | Sale/Marketing | 营销：优惠券/秒杀/首页推荐 | sms_coupon, sms_coupon_history, sms_flash_promotion |
| **UMS** | User | 用户：会员/管理员/角色/资源/积分 | ums_member, ums_admin, ums_role, ums_resource, ums_integration_consume_setting |
| **CMS** | Content | 内容：专题/帮助/优选 | cms_subject, cms_help |

## ⭐ 下单主链路（最重要，深度见记忆 `order-chain-findings`）

**流程**：`/sso/login` → `/cart/add` → `/order/generateConfirmOrder`(只读预览) → `/order/generateOrder` → `/order/paySuccess` → `/order/confirmReceiveOrder`
源码核心：[OmsPortalOrderServiceImpl.java](../mall-swarm/mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java)。

### 库存模型（断言对象）
`pms_sku_stock(stock, lock_stock)`，可用量 **realStock = stock − lock_stock**：
| 事件 | 效果 | 证据 |
|---|---|---|
| 下单 generateOrder | `lock_stock += qty` | OmsPortalOrderServiceImpl:727-733 |
| 支付 paySuccess | `stock −= qty` 且 `lock_stock −= qty` | [PortalOrderDao.xml:50-68](../mall-swarm/mall-portal/src/main/resources/dao/PortalOrderDao.xml) |
| 取消/超时 | `lock_stock −= qty` | PortalOrderDao.xml:77-90 |

下单前置门槛 `hasStock`：`realStock` 为空/≤0/<下单量 → `Asserts.fail("库存不足，无法下单")`。

### 订单状态机 `oms_order.status`
`0 待付款 →(paySuccess)→ 1 待发货 →(admin 发货)→ 2 已发货 →(confirmReceiveOrder)→ 3 已完成`；
`0 →(cancel/超时)→ 4 已关闭`；`5 无效订单`。`deleteOrder` 仅允许 status∈{3,4}（置 delete_status=1）。

### 金额公式（BigDecimal，用于断言）
- `payAmount = totalAmount + freight(恒为0) − promotionAmount − couponAmount − integrationAmount`
- 券分摊/件 = `(单品原价 / 可用商品总价) × 券面额`；按券 `useType`：0全场/1指定分类/2指定商品
- 积分抵扣 = `useIntegration / use_unit`；门槛：`≥use_unit`、`≤会员积分`、`≤totalAmount × maxPercent/100`、与券共用需 `coupon_status=1`
- 促销 `reduceAmount` 按 `promotion_type`：1单品(原价−促销价)/3阶梯(原价−折扣×原价)/4满减((原价/SPU总价)×减额)/其它=0（[OmsPromotionServiceImpl.java:42-108](../mall-swarm/mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPromotionServiceImpl.java)）

### 订单号
18 位：`yyyyMMdd(8) + sourceType(2) + payType(2) + Redis自增(6+)`（OmsPortalOrderServiceImpl:439-454）。

### 超时自动取消（异步/最终一致）
generateOrder 发 RabbitMQ 延迟消息（TTL = `normal_order_overtime × 60 × 1000` ms）→ 死信转发 `mall.order.cancel` → `CancelOrderReceiver` → `cancelOrder`（**仅对 status=0 生效**，已付款则空操作）。
组件：[RabbitMqConfig.java](../mall-swarm/mall-portal/src/main/java/com/macro/mall/portal/config/RabbitMqConfig.java)、[QueueEnum.java](../mall-swarm/mall-portal/src/main/java/com/macro/mall/portal/domain/QueueEnum.java)、CancelOrderSender/Receiver。

## 认证与 RBAC 链路

- 登录→Sa-Token 发 JWT（admin `StpUtil` / member `StpMemberUtil`，两套）。
- 网关对受保护路径校验登录态；管理端再按 Redis `auth:pathResourceMap` 校验资源权限。
- 权限数据：`ums_admin`—`ums_role`—`ums_resource` 多对多；admin 改资源时刷新 Redis 权限表（UmsResourceServiceImpl:85-86）。
- 详见 [02-code-context.md](02-code-context.md) 鉴权段。

## 优惠券模型

- `sms_coupon`：`use_type`(0全场/1分类/2商品)、`amount`(面额)、`per_limit`、有效期、`publish_count/receive_count/use_count`。
- `sms_coupon_history`：会员领券记录，`use_status`(0未用/1已用/2已过期)。
- 链路：admin 建券 → 会员 `/member/coupon/add/{couponId}` 领取 → 下单 `couponId` 抵扣并核销(use_status=1) → 取消/超时退券(use_status=0)。

## 积分（Integration）规则

`ums_integration_consume_setting`（实库 id=1）：`use_unit=100`(100积分=1元)、`max_percent_per_order=50`(最多抵50%)、`coupon_status=1`(可与券共用)。
下单赠送积分/成长值来自商品 `gift_point/gift_growth`，写入 `oms_order.integration/growth`。

## ⭐ 业务不变量（断言锚点）

1. **库存守恒**：下单仅动 lock；支付 stock 与 lock 同减；取消仅退 lock。一笔完整"下单→支付"后 `stock` 净减 qty、`lock_stock` 复原。
2. **金额自洽**：`payAmount` = 上述公式；订单各 item `realAmount` 之和与订单金额一致。
3. **状态单调**：status 只能按状态机迁移；非法迁移应被 `Asserts.fail` 拦截（如确认未发货订单）。
4. **回滚完整**：取消/超时后，券 `use_status` 复 0、积分复原、lock_stock 复原，三者缺一即缺陷。
5. **归属隔离**：会员只能操作自己的订单（confirmReceive/cancel/delete 有 `member.id == order.member_id` 校验）——**但 `paySuccess` 无此校验**（见 badcase）。

## 实库数据锚点（localhost:23306 / 库 mall）

- 会员 `test`(id=1) integration=3900, 3 地址(默认 id=4)；管理员 `admin`/macro123。
- 商品：id=30 `promotion_type=0`（最干净基线）；26/29 单品、27 阶梯、28 满减；product 26 SKU id 110-113，stock≈500，lock=0。
- `oms_order_setting`(id=1)：normal_order_overtime=**120min**、confirm_overtime=15d。
