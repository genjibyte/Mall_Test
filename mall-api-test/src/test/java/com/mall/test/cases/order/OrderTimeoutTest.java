package com.mall.test.cases.order;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.OrderFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1 · 订单超时自动取消（链路 #3 的时间触发部分）。
 * 把未付款订单 create_time 回拨到超过 normal_order_overtime(默认120min)，调用 cancelTimeOutOrder，
 * 断言该订单被关闭(status=4)。逐单回滚(库存/券/积分)由 OrderCancelTest/OrderCouponTest 覆盖。
 * 说明：MQ 延迟队列(60s+)走相同的 cancelOrder 逻辑，此处用同步扫描端口做快速确定性验证。
 */
@Epic("下单主链路")
@Feature("订单超时自动取消")
class OrderTimeoutTest {

    private final OrderClient order = new OrderClient();
    private final String token = TokenFactory.memberToken();
    private final long memberId = MemberFixture.memberId(TestConfig.memberUsername());

    @Test
    @DisplayName("超时未付款订单被自动关闭")
    void overdue_unpaid_order_is_auto_cancelled() {
        final int qty = 1;
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(qty);

        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, qty);
        ApiResponse gen = order.generateOrder(token, addressId, 1, List.of(cartId));
        assertSuccess(gen);
        long orderId = gen.dataLong("order", "id");
        assertEquals(0, OrderFixture.status(orderId), "下单后应为待付款(0)");

        // 模拟超时：回拨创建时间 3 小时（> 默认 120min 阈值）
        OrderFixture.backdateCreateTime(orderId, 3);

        // 触发超时扫描取消
        assertSuccess(order.cancelTimeOutOrder(token));

        assertEquals(4, OrderFixture.status(orderId), "超时后订单应被自动关闭(4)");
    }
}
