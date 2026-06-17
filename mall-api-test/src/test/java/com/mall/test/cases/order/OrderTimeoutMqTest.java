package com.mall.test.cases.order;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.OrderFixture;
import com.mall.test.fixture.OrderSettingFixture;
import com.mall.test.fixture.RabbitFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 超时取消 · RabbitMQ 延迟队列**真实**超时路径（@Tag("slow")，默认排除，-Pslow 跑）。
 * 区别于 OrderTimeoutTest 的同步 cancelTimeOutOrder：本用例验证 generateOrder→TTL队列→DLX→CancelOrderReceiver
 * 的真实异步链路。降 normal_order_overtime=1 分钟，下单后轮询至 status=4，并校验锁库存被释放（真实超时走 cancelOrder，
 * 与用户取消一致，区别于 R8 管理员关单不退库存）。
 */
@Epic("超时取消")
@Feature("MQ 延迟队列真实超时")
@Tag("slow")
class OrderTimeoutMqTest {

    private final OrderClient order = new OrderClient();

    private SkuStockFixture.OrderableSku sku;
    private SkuStockFixture.SkuState baseline;
    private int savedOvertime = -1;

    @AfterEach
    void restore() {
        if (savedOvertime > 0) {
            OrderSettingFixture.setNormalOrderOvertime(savedOvertime);
        }
        if (sku != null && baseline != null) {
            SkuStockFixture.setLockStock(sku.skuId(), baseline.lockStock());
        }
        sku = null;
        baseline = null;
        savedOvertime = -1;
    }

    @Test
    @DisplayName("未付款订单经 MQ 延迟队列真实超时 自动关闭(4) 且释放锁库存")
    void unpaid_order_auto_closed_via_mq_delay_queue() {
        String token = TokenFactory.memberToken();
        long addressId = MemberFixture.defaultAddressId(MemberFixture.memberId(TestConfig.memberUsername()));

        // 关键：先清空 ttl 队列，消除既有长 TTL 消息的队头阻塞，确保本单 60s 消息按时死信
        RabbitFixture.purgeQueue(RabbitFixture.ORDER_CANCEL_TTL_QUEUE);

        // normal_order_overtime=1 分钟（TTL 在下单发消息时按当前值计算，必须先设置）
        savedOvertime = OrderSettingFixture.normalOrderOvertime();
        OrderSettingFixture.setNormalOrderOvertime(1);

        sku = SkuStockFixture.findOrderableNoPromo(1);
        baseline = SkuStockFixture.read(sku.skuId());
        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, 1);

        ApiResponse gen = order.generateOrder(token, addressId, 1, List.of(cartId));
        assertSuccess(gen);
        long orderId = gen.dataLong("order", "id");
        assertEquals(0, OrderFixture.status(orderId), "下单后应为待付款(0)");
        assertEquals(baseline.lockStock() + 1, SkuStockFixture.read(sku.skuId()).lockStock(), "下单应锁定库存");

        // 等待 MQ 延迟队列触发自动取消：TTL=60s + 死信/消费延迟，最多给 110s，前 55s 不轮询
        Awaitility.await("MQ 延迟超时自动关闭订单")
                .atMost(Duration.ofSeconds(110))
                .pollDelay(Duration.ofSeconds(55))
                .pollInterval(Duration.ofSeconds(5))
                .until(() -> OrderFixture.status(orderId) == 4);

        // 真实超时(cancelOrder)应释放锁定库存——与用户取消一致，区别于 R8
        assertEquals(baseline.lockStock(), SkuStockFixture.read(sku.skuId()).lockStock(),
                "MQ 超时取消应释放锁定库存 lock_stock 复原");
    }
}
