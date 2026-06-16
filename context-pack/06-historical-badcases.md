# 06 · 历史 Badcase

> 两类：**A. 源码级业务缺陷**（＝高价值测试靶点，框架应主动验证）；**B. 部署/运行踩坑**（环境层，避免误判为产品 bug）。
> A 类来自源码研读，B 类来自实际部署 + 社区 issue。每条标注证据。

---

## A. 源码级业务缺陷（测试靶点）

> 这些是**被测系统的真实弱点**，不是测试环境问题。框架应按"正确行为"断言并打标 `@KnownDefect`。

### R1 · `paySuccess` 非幂等 — 重复支付重复扣库存
- **症状**：对同一 `orderId` 调用 `/order/paySuccess` 两次，真实库存被扣两次、`lock_stock` 变负。
- **根因**：`updateSkuStock` 是纯算术 `stock = stock − qty`，无状态/幂等保护；`paySuccess` 也不校验订单当前 status。
- **证据**：[OmsPortalOrderServiceImpl.java:253-265](../mall-swarm/mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java) + [PortalOrderDao.xml:50-68](../mall-swarm/mall-portal/src/main/resources/dao/PortalOrderDao.xml)
- **测试**：下单→paySuccess→再 paySuccess；断言 `stock` 只应净减一次（预期失败＝命中缺陷）。

### R2 · `paySuccess` 无归属校验 — 越权支付
- **症状**：任意登录会员可对**他人** `orderId` 调 paySuccess，将其置为已支付。
- **根因**：`paySuccess(orderId, payType)` 不校验 `order.member_id == 当前会员`。
- **证据**：OmsPortalOrderServiceImpl:253（对比 confirmReceiveOrder:340 有归属校验）。
- **测试**：A 会员 token 支付 B 会员订单；断言应被拒（实际会成功＝缺陷）。

### R3 · 下单非事务 — 中段失败状态残留
- **症状**：generateOrder 在锁库存/写订单后若某步异常，已产生的副作用不回滚。
- **根因**：`generateOrder` 及其类**无 `@Transactional`**。
- **证据**：OmsPortalOrderServiceImpl:93（方法）/:32（类）均无事务注解。
- **测试**：构造中段失败（如不可用券在后段），查 `lock_stock`/订单是否残留。

### R4 · 超卖 — 无并发控制
- **症状**：并发对同一 SKU 下单至库存边界，可能超卖（`stock` 负）。
- **根因**：`lockStock` 是 Java"读-改-写"非原子；扣减 SQL 无 `stock>=qty` 卫语句、无乐观锁/版本号。
- **证据**：OmsPortalOrderServiceImpl:727-733；PortalOrderDao.xml 无 where 卫语句。
- **测试**：N 个并发下单 realStock=N-1 的 SKU，断言成功数 ≤ 库存、不出现负库存。

### R5 · 取消/超时回滚完整性
- **症状（待验证项）**：取消后券/积分/库存三者回滚需全部正确。
- **根因/逻辑**：cancelOrder 同时做 releaseSkuStockLock + updateCouponStatus(0) + 退积分；任一遗漏即缺陷。
- **证据**：OmsPortalOrderServiceImpl:297-325 / cancelTimeOutOrder:268-294。
- **测试**：领券+用积分下单后取消，断言 `sms_coupon_history.use_status=0`、`ums_member.integration` 复原、`lock_stock` 复原。

> 注：R1/R2 在源码层未见专门防护；以"正确电商行为"为预期断言即可暴露。

---

## B. 部署 / 运行踩坑（环境层，本项目实测 + 社区 issue）

> 这些会让"服务起不来/接口不通"，**别误判为产品缺陷**。多数已在本地部署中解决并固化到 `deploy/`。

### B1 · ES 缺 IK 分词插件 → mall-search 启动失败
- **症状**：mall-search 启动报 `mapper_parsing_exception: analyzer [ik_max_word] has not been configured`，bean 创建失败、应用退出。
- **根因**：EsProduct 索引 `pms` 的 mapping 用 `ik_max_word`，ES 容器默认无 IK 插件。
- **修复**：`docker exec mall-elasticsearch elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/7.17.3` → `docker restart mall-elasticsearch`。
- **注意**：本环境 IK 未挂卷，`compose down` 重建会丢失，需重装。
- **社区印证**：[mall-swarm#144](https://github.com/macrozheng/mall-swarm/issues/144)、[#134](https://github.com/macrozheng/mall-swarm/issues/134)。

### B2 · Nacos 2.x gRPC 端口未映射 → 注册/拉配置失败
- **症状**：服务连 Nacos 失败（HTTP 通但注册不上）。
- **根因**：Nacos 2.x 客户端按 `httpPort+1000` 连 gRPC（18848→19848）；只映射 8848 不够。
- **修复**：compose 同时映射 `19848:9848`（及 19849:9849）。

### B3 · 端口冲突（基础设施 + 服务）
- **症状**：容器 `port is already allocated`；mall-admin `Port 8080 was already in use`。
- **根因**：本机已有其它 QA 栈（yudao-qa/alsys/order-test）+ 原生 MySQL 占用 3306/6379/8848/9200/8080/13306…
- **修复**：基础设施全部重映射高端口；mall-admin 服务端口→18080（`run-services.ps1` 的 `portMap`）。详见 [04-runtime-environment.md](04-runtime-environment.md)。

### B4 · Maven 打包触发 docker 镜像构建 → 失败
- **症状**：`mvn install` 在 package 阶段执行 `docker:build`，连远程 `dockerHost http://192.168.3.101:2375` 失败/卡住。
- **根因**：父 pom 的 fabric8 docker-maven-plugin 把 `build` 绑定到 package 且 dockerHost 写死为作者内网。
- **修复**：构建加 `-Ddocker.skip=true`。
- **社区印证**：[mall-swarm#137 Maven packaging build failure](https://github.com/macrozheng/mall-swarm/issues/137)。

### B5 · Nacos 配置中心缺配置 → 服务启动失败
- **症状**：服务 `spring.config.import: nacos:mall-xxx-dev.yaml` 找不到 dataId 而失败。
- **根因**：dev 也强依赖 Nacos 配置中心；仓库 `config/` 下文件需导入 Nacos（且**无 `config/auth/`**，mall-auth-dev.yaml 需自建）。
- **修复**：用 Nacos Open API 把 `config/*/mall-*-dev.yaml` POST 进 `DEFAULT_GROUP`，并补一份最小 `mall-auth-dev.yaml`。

### B6 · PowerShell 脚本 UTF-8 中文乱码 → 解析失败
- **症状**：`.ps1` 含中文时 PS 5.1 按 GBK 读取，字符串截断、`TerminatorExpectedAtEndOfString` 解析报错，脚本整体不执行。
- **根因**：Write 默认 UTF-8 无 BOM，Windows PowerShell 5.1 按系统 ANSI(GBK) 读脚本。
- **修复**：脚本正文用 **ASCII/英文**（`run-services.ps1`/`stop-services.ps1` 已遵循）。

### B7 · 其它社区已知 issue（部署/鉴权相关，备查）
| issue | 现象 | 关联本包 |
|---|---|---|
| [#154](https://github.com/macrozheng/mall-swarm/issues/154) | 登录后该用 StpMemberUtil 还是 StpUtil | 双账号机制，见 [02](02-code-context.md) |
| [#146](https://github.com/macrozheng/mall-swarm/issues/146) | 网关启动 Knife4jAutoConfiguration 失败 | 网关/文档聚合版本兼容 |
| [#145](https://github.com/macrozheng/mall-swarm/issues/145) | 经网关访问 SSO 登录 403/OPTIONS | CORS 预检，网关已放行 OPTIONS |
| [#139](https://github.com/macrozheng/mall-swarm/issues/139) | portal 与其它模块 DB 凭据不一致 | 配置核对 |
| [#137](https://github.com/macrozheng/mall-swarm/issues/137) | Maven 打包失败 | 见 B4 |

---

## 维护

发现新缺陷/新坑 → 追加到对应小节（A 业务 / B 环境），写清 症状·根因·修复/断言·证据。修复后将 A 类 `@KnownDefect` 用例转为常驻守护。
