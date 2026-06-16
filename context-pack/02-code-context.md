# 02 · 代码上下文（Code Context）

> 全部结论来自 `../mall-swarm/` 逐文件研读。引用以 `模块/路径:行` 标注。**仓库只读**。

## 技术栈基线（根 [pom.xml](../mall-swarm/pom.xml)）

Spring Cloud `2025.0.2` + Spring Cloud Alibaba `2025.0.0.0`（Nacos 注册/配置）· Spring Boot `3.5.x` · **Java 17** ·
**Sa-Token `1.42`**（认证，已取代旧版 Spring Security OAuth2 —— README 的"组织结构"段仍写 OAuth2，**以源码为准**）·
MyBatis + MBG + PageHelper + Druid · Elasticsearch 7.17.3（spring-data-elasticsearch / ELC）· RabbitMQ · Redis · MongoDB · Knife4j/SpringDoc · Alipay SDK · MinIO/OSS。

## 模块图（9 模块）

| 模块 | 端口* | 职责 | 编译依赖 | 运行期依赖 |
|---|---|---|---|---|
| mall-gateway | 8201 | 网关路由 + Sa-Token 全局鉴权 + 白名单 + CORS + 文档聚合 | mall-common | Redis(token+权限表)、Nacos、各服务(lb://) |
| mall-auth | 8401 | 统一登录门面，按 clientId Feign 委托 admin/portal | mall-common | Nacos、Feign→admin/portal |
| mall-admin | 18080 | 商品/订单/营销/权限/内容**管理端** | mall-mbg | MySQL、Redis、(MinIO/OSS) |
| mall-portal | 8085 | 会员/购物车/下单/支付/退货**会员端** | mall-mbg | MySQL、Redis、RabbitMQ、MongoDB、(Alipay) |
| mall-search | 8081 | 商品 ES 搜索（DB→ES 导入+检索） | mall-mbg | MySQL(读)、Elasticsearch |
| mall-mbg | — | MBG 自动生成实体+Mapper（152 model/76 mapper），共享数据层 | mall-common | — |
| mall-common | — | 统一响应/异常/Redis/常量 工具库 | — | — |
| mall-monitor | 8101 | Spring Boot Admin 监控（无业务接口） | — | — |
| mall-demo | 8082 | Feign 调用教学演示（非业务） | mall-mbg | — |

\* 端口为本地部署实际值（admin 重映射 18080），见 [04-runtime-environment.md](04-runtime-environment.md)。

## 包与代码约定

- 包根 `com.macro.mall`（portal 在 `com.macro.mall.portal`，search 在 `...search`，admin/gateway 直接在 `com.macro.mall`）。
- 分层：`controller`（REST）→ `service` + `service/impl` → `dao`(自定义 SQL，XML 在 `resources/dao/*.xml`) / MBG `mapper`（`com.macro.mall.mapper`）。
- 实体：MBG 生成于 mall-mbg `com.macro.mall.model.*`；DTO/入参在各模块 `domain`。
- 命名前缀＝业务域：`Pms*`商品 `Oms*`订单 `Sms*`营销 `Ums*`用户 `Cms*`内容（见 [03-business-context.md](03-business-context.md)）。

## ⭐ 统一响应契约（断言基石）

所有接口返回 `CommonResult<T>`（[mall-common/.../api/CommonResult.java](../mall-swarm/mall-common/src/main/java/com/macro/mall/common/api/CommonResult.java)）：
```json
{ "code": 200, "message": "操作成功", "data": { ... } }
```
`code` 取值（[ResultCode.java](../mall-swarm/mall-common/src/main/java/com/macro/mall/common/api/ResultCode.java)）：

| code | 含义 | 触发 |
|---|---|---|
| 200 | 操作成功 | 正常 |
| 500 | 操作失败 | 业务失败：`Asserts.fail(msg)`→`ApiException`→`GlobalExceptionHandler` |
| 404 | 参数校验失败 | `validateFailed` |
| 401 | 未登录/token 过期 | 网关 Sa-Token 拦截 |
| 403 | 无权限 | 网关权限校验不通过 |

**关键：`code` 是业务码，与 HTTP 状态解耦。** 业务失败时 HTTP 往往仍是 200，但 `body.code=500`。
→ 断言**必须**先判 `body.code`，不能只看 HTTP status。分页用 `CommonPage<T>{pageNum,pageSize,total,totalPage,list}`。

## ⭐ 鉴权机制（Sa-Token 双账号）

经网关 [SaTokenConfig.java:47-74](../mall-swarm/mall-gateway/src/main/java/com/macro/mall/config/SaTokenConfig.java)：
- `/mall-portal/**` → `StpMemberUtil.checkLogin()`（**会员**账号体系）
- `/mall-admin/**` → `StpUtil.checkLogin()`（**管理员**账号体系）+ 动态权限校验
- 管理端权限：网关读 Redis 哈希 `auth:pathResourceMap`（由 admin `UmsResourceServiceImpl` 写入），调用 `StpUtil.checkPermissionOr()`
- token 放请求头 `Authorization: Bearer <token>`（[gateway application.yml:104-124](../mall-swarm/mall-gateway/src/main/resources/application.yml)）
- **admin 与 member 是两套独立 token**，互不通用（社区高频困惑点，github issue #154）。

登录入口（均在网关白名单）：
- 管理员：`POST /mall-admin/admin/login`（JSON body `{username,password}`）→ `data.token`
- 会员：`POST /mall-portal/sso/login`（form `username&password`）→ `data.token`
- 或统一：`POST /mall-auth/auth/login?clientId=&username=&password=`（按 clientId Feign 委托）

网关白名单（无需 token，[application.yml:56-79](../mall-swarm/mall-gateway/src/main/resources/application.yml)）：`/mall-auth/**`、`/mall-search/**`、`/mall-portal/sso/login|register|getAuthCode`、`/mall-portal/home/**|product/**|brand/**|alipay/**`、`/mall-admin/admin/login|register`、`/doc.html`、`/*/v3/api-docs` 等。

## 业务入口清单（按链路）

| 链路 | 关键端点（网关前缀略） |
|---|---|
| 下单 | `/cart/add`、`/cart/list/promotion`、`/order/generateConfirmOrder`、`/order/generateOrder`、`/order/paySuccess`、`/order/cancelUserOrder`、`/order/confirmReceiveOrder`、`/order/list`、`/order/detail/{id}` |
| 认证/RBAC | `/admin/login`、`/admin/info`、`/admin/role/update`、`/sso/login`、`/sso/info`、`/sso/register` |
| 搜索 | `/esProduct/importAll`、`/esProduct/search`、`/esProduct/search/simple`、`/esProduct/recommend/{id}` |
| 优惠券 | `/member/coupon/add/{couponId}`、`/member/coupon/list`、`/member/coupon/listHistory` |
| 商品管理(admin) | `/product/create`、`/product/update/publishStatus`、`/product/list` |

## 代码惯用法（影响断言/数据准备）

- 业务校验失败统一走 `Asserts.fail("...")`（[mall-common/.../exception/Asserts.java](../mall-swarm/mall-common/src/main/java/com/macro/mall/common/exception/Asserts.java)）→ `code=500` + 该 message。
- 金额一律 `BigDecimal`，比对需用 `compareTo`/scale 对齐，勿用 `equals` 或 double。
- 分页用 PageHelper（`PageHelper.startPage`），返回 `CommonPage`。
- "当前用户"来自 Sa-Token 会话（`memberService.getCurrentMember()`），故大多数会员接口**不显式传 memberId**，靠 token 推断。
- 真正的登录逻辑在 admin/portal（Sa-Token 发 JWT），mall-auth 只是 Feign 门面。
