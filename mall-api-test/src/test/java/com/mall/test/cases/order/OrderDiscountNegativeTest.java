package com.mall.test.cases.order;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.CouponFixture;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.mall.test.core.ApiAssertions.assertFailedWithMessage;

/**
 * P1 · 券/积分使用负例：门槛不满足的券、低于门槛的积分应被拒（下单前校验，无副作用）。
 */
@Epic("下单主链路")
@Feature("券与积分负例")
class OrderDiscountNegativeTest {

    private final OrderClient order = new OrderClient();
    private final String token = TokenFactory.memberToken();
    private final long memberId = MemberFixture.memberId(TestConfig.memberUsername());
    private CouponFixture.TestCoupon tc;

    @AfterEach
    void cleanup() {
        if (tc != null) { CouponFixture.delete(tc); tc = null; }
        OrderFlow.clearCart(token);
    }

    @Test
    @DisplayName("券门槛未达 使用被拒")
    void coupon_below_min_point_is_rejected() {
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(1);
        // 门槛极高(99999) -> 订单额不达标 -> 不可用
        tc = CouponFixture.createUniversalCoupon(memberId, new BigDecimal("10.00"), new BigDecimal("99999.00"));

        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, 1);
        ApiResponse r = order.generateOrder(token, addressId, 1, List.of(cartId), tc.couponId(), null);
        assertFailedWithMessage(r, "优惠券不可用");
    }

    @Test
    @DisplayName("积分低于最小使用单位 使用被拒")
    void integration_below_unit_is_rejected() {
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(1);

        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, 1);
        // useIntegration=50 < use_unit(100) -> 积分不可用
        ApiResponse r = order.generateOrder(token, addressId, 1, List.of(cartId), null, 50);
        assertFailedWithMessage(r, "积分不可用");
    }
}
