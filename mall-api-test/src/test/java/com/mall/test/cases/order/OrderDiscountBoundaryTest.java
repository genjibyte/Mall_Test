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
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static com.mall.test.core.ApiAssertions.assertAmountEquals;
import static com.mall.test.core.ApiAssertions.assertFailedWithMessage;
import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 下单主链路 · 折扣**边界值 / 等价类**（企业级测试设计：数据驱动）。
 * 积分围绕最小使用单位 use_unit=100 做边界（&lt;unit 拒绝 / ≥unit 受理，边界 99/100）；
 * 优惠券围绕门槛 min_point 与订单额做边界（高于订单额拒绝 / 等于订单额受理）。
 * 数据策略：每次失效会员缓存、teardown 取消订单并还原积分/删券，保证可重复。
 */
@Epic("下单主链路")
@Feature("折扣边界与等价类")
@Story("积分 use_unit 边界 / 优惠券 min_point 边界")
@Owner("mall-qa")
@Severity(SeverityLevel.CRITICAL)
class OrderDiscountBoundaryTest {

    private final OrderClient order = new OrderClient();
    private final String token = TokenFactory.memberToken();
    private final long memberId = MemberFixture.memberId(TestConfig.memberUsername());

    private Long orderId;
    private Integer integrationBefore;
    private CouponFixture.TestCoupon tc;

    @AfterEach
    void cleanup() {
        if (orderId != null) {
            order.cancelUserOrder(token, orderId);
        }
        if (integrationBefore != null) {
            MemberFixture.setIntegration(memberId, integrationBefore); // 补偿 R6
            MemberFixture.invalidateCache(memberId);
        }
        if (tc != null) {
            CouponFixture.delete(tc);
        }
        OrderFlow.clearCart(token);
        orderId = null;
        integrationBefore = null;
        tc = null;
    }

    /** 积分使用边界（use_unit=100）：等价类 {&lt;100 拒绝} / {≥100 受理}，边界值 99/100。 */
    @ParameterizedTest(name = "useIntegration={0} 期望 {1}")
    @CsvSource({"50, reject", "99, reject", "100, accept", "200, accept"})
    @DisplayName("积分使用边界 围绕最小单位100")
    void integration_use_boundary(int useIntegration, String expect) {
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(1);
        MemberFixture.invalidateCache(memberId);
        integrationBefore = MemberFixture.integration(memberId);

        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, 1);
        ApiResponse r = order.generateOrder(token, addressId, 1, List.of(cartId), null, useIntegration);

        if ("reject".equals(expect)) {
            assertFailedWithMessage(r, "积分不可用");
        } else {
            assertSuccess(r);
            orderId = r.dataLong("order", "id");
            BigDecimal discount = new BigDecimal(useIntegration).divide(new BigDecimal(100));
            assertAmountEquals(sku.price().subtract(discount), r.dataDecimal("order", "payAmount"), "积分抵扣后应付");
            assertEquals(integrationBefore - useIntegration, MemberFixture.integration(memberId), "应扣减对应积分");
        }
    }

    /** 券门槛边界：min_point 高于订单额（price+1）→ 不可用。 */
    @Test
    @DisplayName("券门槛高于订单额 使用被拒")
    void coupon_min_point_above_total_rejected() {
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(1);
        tc = CouponFixture.createUniversalCoupon(memberId, new BigDecimal("10.00"), sku.price().add(BigDecimal.ONE));

        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, 1);
        ApiResponse r = order.generateOrder(token, addressId, 1, List.of(cartId), tc.couponId(), null);
        assertFailedWithMessage(r, "优惠券不可用");
    }

    /** 券门槛边界：min_point 等于订单额（price）→ 恰好满足，抵面额。 */
    @Test
    @DisplayName("券门槛等于订单额 恰好可用 抵面额")
    void coupon_min_point_equal_total_accepted() {
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(1);
        tc = CouponFixture.createUniversalCoupon(memberId, new BigDecimal("10.00"), sku.price());

        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, 1);
        ApiResponse r = order.generateOrder(token, addressId, 1, List.of(cartId), tc.couponId(), null);
        assertSuccess(r);
        orderId = r.dataLong("order", "id");
        assertAmountEquals(sku.price().subtract(new BigDecimal("10.00")),
                r.dataDecimal("order", "payAmount"), "券后应付=price-面额");
    }
}
