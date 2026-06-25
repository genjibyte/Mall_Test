package com.mall.test.cases.chaos;

import com.mall.test.client.OrderClient;
import com.mall.test.client.SearchClient;
import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;
import com.mall.test.fixture.DataHygieneFixture;
import com.mall.test.fixture.IsolatedMemberFixture;
import com.mall.test.fixture.IsolatedSkuFixture;
import com.mall.test.fixture.MiddlewareFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中间件韧性 · 依赖故障注入（可控混沌）。
 * **手动运行**（会 docker stop 共享中间件，绝不进默认线）：
 *   mvn -Dtest=MiddlewareResilienceTest "-Djunit.jupiter.conditions.deactivate=*" test
 * 边界：可控(docker 注入)、可复现(teardown 强制 start 恢复 + 等待重连)。每个用例验证"依赖宕机下的降级行为"。
 */
@Epic("中间件韧性")
@Feature("依赖故障注入(可控混沌)")
@Owner("mall-qa")
@Severity(SeverityLevel.MINOR)
@Tag("chaos")
@Disabled("chaos: 手动运行，见类注释")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MiddlewareResilienceTest {

    private final SearchClient search = new SearchClient();
    private final OrderClient order = new OrderClient();

    @AfterEach
    void restoreAll() {
        // 兜底：无论用例是否中途失败，强制恢复所有可能被停的中间件
        MiddlewareFixture.start(MiddlewareFixture.ES);
        MiddlewareFixture.start(MiddlewareFixture.RABBITMQ);
        MiddlewareFixture.start(MiddlewareFixture.REDIS);
        waitSearchRecovered();
    }

    @Test
    @DisplayName("ES 宕：搜索降级失败，但前台浏览不受影响(故障域隔离)")
    void es_down_isolates_search_failure() {
        assertTrue(search.searchSimple(null, 0, 1).isSuccess(), "前置：搜索应可用");

        MiddlewareFixture.stop(MiddlewareFixture.ES);
        try {
            ApiResponse s = search.searchSimple("P20", 0, 5);
            assertFalse(s.isSuccess(), "ES 宕时搜索不应成功(应降级/报错): " + s);

            // 故障域隔离：不依赖 ES 的前台首页仍应 200
            ApiResponse home = ApiResponse.from(RestClient.given().get("/mall-portal/home/content"));
            assertTrue(home.isSuccess(), "ES 宕不应波及前台首页(故障域隔离): " + home);
        } finally {
            MiddlewareFixture.start(MiddlewareFixture.ES);
            waitSearchRecovered();
        }
    }

    @Test
    @DisplayName("RabbitMQ 宕：下单一致性(超时取消消息发不出时不应残留孤儿订单/锁库存)")
    void rabbitmq_down_order_consistency() {
        long memberId = IsolatedMemberFixture.ensure();           // 隔离会员，零漂移
        String token = IsolatedMemberFixture.token();
        long addressId = IsolatedMemberFixture.addressId();
        SkuStockFixture.OrderableSku sku = IsolatedSkuFixture.ensure(); // 满库存、锁0
        SkuStockFixture.SkuState before = SkuStockFixture.read(sku.skuId());

        // 加购在 MQ 正常时完成，把故障精确注入到 generateOrder
        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, 1);

        MiddlewareFixture.stop(MiddlewareFixture.RABBITMQ);
        ApiResponse gen;
        try {
            gen = order.generateOrder(token, addressId, 1, List.of(cartId));
        } finally {
            MiddlewareFixture.start(MiddlewareFixture.RABBITMQ);
        }

        int lockDelta = SkuStockFixture.read(sku.skuId()).lockStock() - before.lockStock();
        System.out.printf("[chaos] RabbitMQ down → generateOrder success=%s code=%d lockΔ=%d%n",
                gen.isSuccess(), gen.code(), lockDelta);
        try {
            // 一致性不变量：成功→锁库存=+1；失败→不应残留锁库存(否则=孤儿订单/库存泄漏)
            if (gen.isSuccess()) {
                assertEquals(1, lockDelta, "MQ 宕但下单成功：应正常锁定库存(只是无超时取消调度)");
            } else {
                assertEquals(0, lockDelta,
                        "MQ 宕下单失败却残留锁库存=孤儿订单/库存泄漏(下单非事务 R3 的韧性放大): lockΔ=" + lockDelta);
            }
        } finally {
            DataHygieneFixture.purgeMemberOrders(memberId);             // 清隔离会员订单
            SkuStockFixture.setLockStock(sku.skuId(), before.lockStock()); // 复位锁库存
        }
    }

    @Test
    @DisplayName("Redis 宕：鉴权降级(token 存 Redis，已登录请求失败)，公共接口可用性观测")
    void redis_down_auth_degrades() {
        String token = IsolatedMemberFixture.token(); // Redis 正常时登录，session 落 Redis
        assertTrue(memberInfo(token).isSuccess(), "前置：已登录可访问 /sso/info");

        MiddlewareFixture.stop(MiddlewareFixture.REDIS);
        try {
            ApiResponse authed = memberInfo(token);
            System.out.printf("[chaos] Redis down → /sso/info success=%s code=%d%n", authed.isSuccess(), authed.code());
            assertFalse(authed.isSuccess(), "Redis 宕时已登录校验应降级失败(token 存 Redis): " + authed);

            // 公共接口可用性：观测(首页若 Redis 缓存则不可用，否则读 DB 仍可用)
            ApiResponse home = ApiResponse.from(RestClient.given().get("/mall-portal/home/content"));
            System.out.printf("[chaos] Redis down → home/content success=%s code=%d%n", home.isSuccess(), home.code());
        } finally {
            MiddlewareFixture.start(MiddlewareFixture.REDIS);
        }
        // 恢复：Redis 回来后重新登录应可用(旧 session 随 Redis 重启丢失)
        waitAuthRecovered();
    }

    private ApiResponse memberInfo(String token) {
        return ApiResponse.from(RestClient.givenAuth(token).get("/mall-portal/sso/info"));
    }

    private void waitAuthRecovered() {
        try {
            Awaitility.await("鉴权恢复")
                    .atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(3)).ignoreExceptions()
                    .until(() -> memberInfo(IsolatedMemberFixture.token()).isSuccess());
        } catch (Exception e) {
            System.err.println("[chaos] 鉴权未在 60s 内恢复: " + e.getMessage());
        }
    }

    private void waitSearchRecovered() {
        try {
            Awaitility.await("搜索恢复")
                    .atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(5))
                    .ignoreExceptions()
                    .until(() -> search.searchSimple(null, 0, 1).isSuccess());
        } catch (Exception e) {
            System.err.println("[chaos] 搜索未在 90s 内恢复: " + e.getMessage());
        }
    }
}
