package com.mall.test.cases.order;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.CouponFixture;
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
 * P1 · 优惠券：全场通用券下单应付减面额；取消后券回退未使用、库存复原。
 * 覆盖金额组合(券分摊，单品 qty=1 即全额)与回滚不变量 R5(券)。
 */
@Epic("下单主链路")
@Feature("优惠券抵扣与回滚")
class OrderCouponTest {

    private final OrderClient order = new OrderClient();
    private final String token = TokenFactory.memberToken();
    private final long memberId = MemberFixture.memberId(TestConfig.memberUsername());
    private CouponFixture.TestCoupon coupon;

    @AfterEach
    void cleanup() {
        if (coupon != null) { CouponFixture.delete(coupon); coupon = null; }
        OrderFlow.clearCart(token);
    }

    @Test
    @DisplayName("全场券下单 应付减面额 取消后券回退未使用")
    void universal_coupon_discount_then_cancel_restores() {
        final int qty = 1;
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(qty);
        SkuStockFixture.SkuState before = SkuStockFixture.read(sku.skuId());
        BigDecimal amount = new BigDecimal("10.00");
        coupon = CouponFixture.createUsableUniversalCoupon(memberId, amount);

        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, qty);

        // 下单用券：payAmount = price - amount，券核销
        ApiResponse gen = order.generateOrder(token, addressId, 1, List.of(cartId), coupon.couponId(), null);
        assertSuccess(gen);
        long orderId = gen.dataLong("order", "id");
        assertAmountEquals(sku.price().subtract(amount), gen.dataDecimal("order", "payAmount"), "券后应付");
        assertEquals(1, CouponFixture.historyUseStatus(coupon.couponId(), memberId), "下单后券应为已使用(1)");

        // 取消：券回退未使用 + 锁库存复原
        assertSuccess(order.cancelUserOrder(token, orderId));
        assertEquals(4, OrderFixture.status(orderId), "取消后订单应为已关闭(4)");
        assertEquals(0, CouponFixture.historyUseStatus(coupon.couponId(), memberId), "取消后券应回退未使用(0)");
        assertEquals(before.lockStock(), SkuStockFixture.read(sku.skuId()).lockStock(), "取消后锁库存复原");
    }
}
