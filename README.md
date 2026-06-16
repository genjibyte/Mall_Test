# Mall_Test · mall-swarm 接口自动化测试工程

针对微服务电商系统 [mall-swarm](https://github.com/macrozheng/mall-swarm)（Spring Cloud Alibaba 2025 / Spring Boot 3.5 / Sa-Token / Java 17）的**接口自动化测试**工程：包含上下文/约束包（设计）、测试框架（代码）与本地部署脚本。

> 被测系统 `mall-swarm` 源码本身**不纳入本仓库**（Apache-2.0，单独克隆）。本仓库是围绕它的测试资产。

## 目录

| 目录 | 内容 |
|---|---|
| [context-pack/](context-pack/) | **设计 / 约束上下文包**：目标、边界、代码上下文、业务上下文、运行环境、质量门禁、历史 badcase、交接协议（9 个 MD）。冷启动先读这里。 |
| [mall-api-test/](mall-api-test/) | **测试框架（代码）**：Java + JUnit5 + RestAssured + Allure。分层 client/fixture/core/cases，含已跑通的 P0 下单主链路用例。 |
| [deploy/](deploy/) | **本地部署**：基础设施 docker-compose（隔离端口）+ 服务启停脚本 + 部署说明。 |

## 快速开始

```bash
# 1. 克隆被测系统（与本仓库同级）
git clone https://github.com/macrozheng/mall-swarm.git

# 2. 起基础设施 + 服务（详见 deploy/README.md）
docker compose -f deploy/docker-compose-env.yml up -d
cd mall-swarm && mvn clean install -DskipTests -Ddocker.skip=true && cd ..
powershell -File deploy/run-services.ps1

# 3. 跑接口测试（详见 mall-api-test/README.md）
cd mall-api-test && mvn test
```

## 技术栈

- 被测：Spring Cloud Alibaba 2025、Spring Boot 3.5、Sa-Token、MyBatis、Elasticsearch、RabbitMQ、Redis、MongoDB、Nacos。
- 测试：Java 17、JUnit 5、RestAssured 5、Allure、直连 MySQL 做灰盒校验。

## 关键约定

- 断言**先看 `body.code`**（200/401/403/404/500，与 HTTP 状态解耦），再断 `data`；金额用 `BigDecimal`。
- 鉴权为 Sa-Token **双账号**（admin / member 两套 token），请求头 `Authorization: Bearer <token>`，统一入口走网关 `:8201`。
- 详见 [context-pack/](context-pack/)。

## 致谢

被测系统 [macrozheng/mall-swarm](https://github.com/macrozheng/mall-swarm)（Apache License 2.0）。本仓库仅含测试与部署资产，不含其源码。
