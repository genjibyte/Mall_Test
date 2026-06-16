package com.mall.test.cases.order;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.core.KnownDefect;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.OrderFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.mall.test.core.ApiAssertions.assertAmountEquals;
import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1 · 积分：用 100 积分下单抵 1 元（use_unit=100）。
 * 正确且成立的行为（抵扣金额、扣减积分）作为常驻守护；
 * 取消退还积分因 R6 缺陷不成立，单列为 @KnownDefect（默认跳过）。
 * 下单前失效会员缓存使 getCurrentMember 读最新 DB；teardown 还原积分(补偿 R6)保证可重复。
 */
@Epic("下单主链路")
@Feature("积分抵扣与回滚")
class OrderIntegrationTest {

    private final OrderClient order = new OrderClient();
    private final String token = TokenFactory.memberToken();
    private final long memberId = MemberFixture.memberId(TestConfig.memberUsername());

    private Long orderId;
    private Integer integrationBefore;

    @AfterEach
    void cleanup() {
        if (orderId != null) order.cancelUserOrder(token, orderId); // 还原库存（注意：不还原积分，见 R6）
        if (integrationBefore != null) {                            // 补偿 R6，保证用例可重复
            MemberFixture.setIntegration(memberId, integrationBefore);
            MemberFixture.invalidateCache(memberId);
        }
        OrderFlow.clearCart(token);
        orderId = null;
        integrationBefore = null;
    }

    @Test
    @DisplayName("用100积分下单 抵1元 并扣减100积分")
    void integration_discount_and_deduction() {
        final int qty = 1;
        final int useIntegration = 100; // use_unit=100 -> 抵 1 元
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(qty);

        MemberFixture.invalidateCache(memberId);
        integrationBefore = MemberFixture.integration(memberId);

        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, qty);

        ApiResponse gen = order.generateOrder(token, addressId, 1, List.of(cartId), null, useIntegration);
        assertSuccess(gen);
        orderId = gen.dataLong("order", "id");
        assertAmountEquals(sku.price().subtract(new BigDecimal("1")), gen.dataDecimal("order", "payAmount"), "积分抵扣后应付");
        assertEquals(integrationBefore - useIntegration, MemberFixture.integration(memberId), "下单应扣减积分 100");
    }

    /**
     * 正确预期：用积分下单后取消，积分应回退。实际不会——R6 缺陷。
     * 默认跳过（不阻断门禁）；修复后移除 @KnownDefect 即转为守护。
     */
    @KnownDefect("R6: 积分下单取消后未退还积分（oms_order.use_integration 未持久化）")
    @Test
    @DisplayName("取消积分订单应退还积分 已知缺陷R6")
    void cancel_should_refund_used_integration() {
        final int qty = 1;
        final int useIntegration = 100;
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(qty);

        MemberFixture.invalidateCache(memberId);
        int before = MemberFixture.integration(memberId);
        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, qty);

        ApiResponse gen = order.generateOrder(token, addressId, 1, List.of(cartId), null, useIntegration);
        assertSuccess(gen);
        long oid = gen.dataLong("order", "id");
        assertSuccess(order.cancelUserOrder(token, oid));

        assertEquals(before, MemberFixture.integration(memberId), "取消后积分应回退");
    }
}
