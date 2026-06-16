# P0 测试分析 / 测试理解

> P0 范围的被测理解、测试点、场景设计与判定口径（oracle）。所有预期均经**源码 + 实环境实测**双重确认。
> 配套：约束见 [../../context-pack](../../context-pack)，用例见 `src/test/java/com/mall/test/cases`。

## 1. P0 范围

| 链路 | 场景 | 状态 |
|---|---|---|
| 下单主链路 | 会员 加购→确认单→下单→支付（金额/库存/状态） | ✅ 已实现 `OrderHappyPathTest` |
| 认证 | 管理员/会员登录拿 token → 访问受保护接口 200 | 本次实现 `AuthHappyPathTest` |
| 鉴权(401) | 无 token / 错误 token / 跨账号(会员 token 访问后台) | 本次实现 `AuthGuardTest` |
| 鉴权(403) | 受限角色管理员访问无权限接口 | 本次实现 `AuthGuardTest` |

## 2. 被测理解（要点）

- 统一响应 `{code,message,data}`，`code` 为**业务码**（与 HTTP 状态解耦），断言以 `code` 为准。
- 鉴权为 Sa-Token **双账号**：`/mall-portal/**` 用 `StpMemberUtil`，`/mall-admin/**` 用 `StpUtil` + 资源权限校验（网关读 Redis `auth:pathResourceMap`，实测 31 条规则已加载）。
- 两套 token **互不通用**：会员 token 访问后台 → `StpUtil` 不认 → 401（**非 403**）。
- 后台权限：登录但**缺所需资源** → 403。超管(admin, 角色"超级管理员")拥有全部资源 → 不会 403。
- 下单库存模型：可用量 realStock=stock−lock_stock；下单 lock+qty、支付 stock−qty 且 lock 复原。

## 3. 判定口径（Oracle，实测取值）

| 结果 | code | message | 触发 |
|---|---|---|---|
| 成功 | 200 | 操作成功 | 正常 |
| 未登录/失效 | 401 | 暂未登录或token已经过期 | 无 token / 错误 token / 跨账号 |
| 无权限 | 403 | 没有相关权限 | 登录但缺资源 |
| 业务失败 | 500 | （具体语，如"库存不足，无法下单"） | Asserts.fail |

> 注：401/403 的 message 在 GBK 终端会乱码，实际为合法 UTF-8；断言用 `code`，不依赖 message 中文。

## 4. 测试点（Test Points）

**T-AUTH 认证**
- T-AUTH-01 管理员登录返回 token + tokenHead，且 token 可访问 `/admin/info` 返回角色/菜单。
- T-AUTH-02 会员登录返回 token，且可访问 `/sso/info`。
- T-AUTH-03 登录失败口径（错误密码 → code 500）。〔P1，本次可选〕

**T-GUARD 鉴权**
- T-GUARD-01 无 token 访问受保护会员接口 → 401。
- T-GUARD-02 错误 token 访问受保护会员接口 → 401。
- T-GUARD-03 无 token 访问受保护后台接口 → 401。
- T-GUARD-04 **跨账号**：会员 token 访问后台接口 → 401（验证双账号隔离）。
- T-GUARD-05 **越权**：受限角色管理员访问无权资源接口 → 403；有权接口 → 200（对照）。

**T-ORDER 下单**（已实现，见 `OrderHappyPathTest`）
- T-ORDER-01 金额：确认单/订单 payAmount == price×qty（无促销/券/积分）。
- T-ORDER-02 库存：下单 lock+qty 且 stock 不变；支付 stock−qty 且 lock 复原。
- T-ORDER-03 状态：下单 status=0；支付 status=1。

## 5. 场景设计（含前置/步骤/预期/副作用）

### S-AUTH-HAPPY（T-AUTH-01/02）
- 前置：种子账号 admin/macro123、test/123456 存在。
- 步骤：分别登录 → 取 token → 访问各自体系受保护接口。
- 预期：登录 code 200 且 data.token 非空；`/admin/info` code 200 且 data.roles 含"超级管理员"；`/sso/info` code 200。

### S-GUARD-401（T-GUARD-01/02/03/04）
- 前置：会员 token（test）。
- 步骤/预期：
  - `/mall-portal/order/list` 无 token → **401**
  - `/mall-portal/order/list` Bearer "not-a-real-token" → **401**
  - `/mall-admin/admin/info` 无 token → **401**
  - `/mall-admin/admin/info` 带**会员** token → **401**（跨账号隔离）

### S-GUARD-403（T-GUARD-05）
- **夹具**：把 `productAdmin` 的密码改成与 `admin` 相同的哈希（DB，幂等），使其可用 macro123 登录。该账号绑角色 1（仅商品域 10 个资源：/product/**、/brand/**、/sku/** 等，**无** /order/**）。
- 步骤/预期：
  - productAdmin 登录 → code 200
  - productAdmin → `/mall-admin/product/list` → **200**（有权，对照组）
  - productAdmin → `/mall-admin/order/list` → **403**（缺 /order/** 资源）
- 依据：Redis `auth:pathResourceMap` 含 `/mall-admin/order/**`→订单资源；role 1 资源集实测不含该资源。

## 6. 数据与夹具

| 夹具 | 用途 | 实现 |
|---|---|---|
| TokenFactory | 双账号 token 缓存 | 已有 |
| MemberFixture | 会员 id/默认地址 | 已有 |
| SkuStockFixture | 可下单 SKU/库存差值 | 已有 |
| **AdminFixture** | 使 productAdmin 可登录（403 用） | 本次新增（DB 改密，幂等） |

## 7. 风险与遗留

- S-GUARD-403 修改了 `productAdmin` 密码（持久、幂等、测试环境无害）；不做还原（无已知原密码，且使其可登录无副作用）。
- 跨账号期望是 **401 非 403**（源码层 StpUtil 不识别会员 token）；勿误设为 403。
- 登录失败口径（错误密码 500）、token 过期、并发登录顶号等列入 P1。
- 异步链路（超时取消）、缺陷探针（幂等/越权支付/超卖）列入 P1/P2，不在本批。

## 8. 优先级 → 用例映射

| 场景 | 优先级 | 用例 |
|---|---|---|
| S-ORDER-HAPPY | P0 | OrderHappyPathTest |
| S-AUTH-HAPPY | P0 | AuthHappyPathTest |
| S-GUARD-401 | P0 | AuthGuardTest（4 断言） |
| S-GUARD-403 | P0 | AuthGuardTest（夹具 + 2 断言） |
