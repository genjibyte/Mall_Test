package com.mall.test.cases.order;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.OrderFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Allure;
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
 * P0 · 下单主链路 happy path：会员 加购 → 确认单 → 下单 → 支付。
 * 验证金额（payAmount==price*qty）、库存（下单锁库存、支付扣真实库存释放锁库存）、订单状态流转。
 * 用无促销(promotion_type=0)、不用券/积分，保证金额可精确预期。
 * 断言遵循 context-pack/05 QG1：先看 body.code，副作用查库复核；用 before/after 差值保证可重复。
 */
@Epic("下单主链路")
@Feature("订单生成与支付")
class OrderHappyPathTest {

    private final OrderClient order = new OrderClient();

    private Long skuId;
    private SkuStockFixture.SkuState before;

    @AfterEach
    void restoreStock() {
        // happy path 走完整支付（扣减真实库存且业务上不可回退），用 DB 快照还原保证可重复、不漂移。
        if (skuId != null && before != null) {
            SkuStockFixture.setStock(skuId, before.stock());
            SkuStockFixture.setLockStock(skuId, before.lockStock());
        }
        skuId = null;
        before = null;
    }

    @Test
    @DisplayName("P0 会员下单到支付 金额库存状态全链路正确")
    void member_order_to_pay_happyPath() {
        final int qty = 1;
        String token = TokenFactory.memberToken();
        long memberId = MemberFixture.memberId(TestConfig.memberUsername());
        long addressId = MemberFixture.defaultAddressId(memberId);

        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(qty);
        skuId = sku.skuId();
        before = SkuStockFixture.read(sku.skuId());
        BigDecimal expectedPay = sku.price().multiply(BigDecimal.valueOf(qty));

        // 0-2. 隔离 + 加购，取 cartId
        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, qty);

        // 3. 确认单：应付金额 == price*qty
        Allure.step("生成确认单", () -> {
            ApiResponse confirm = order.generateConfirmOrder(token, List.of(cartId));
            assertSuccess(confirm);
            assertAmountEquals(expectedPay, confirm.dataDecimal("calcAmount", "payAmount"), "确认单应付");
        });

        // 4. 下单：返回订单，status=0 待付款，payAmount 正确
        ApiResponse gen = order.generateOrder(token, addressId, 1, List.of(cartId));
        assertSuccess(gen);
        long orderId = gen.dataLong("order", "id");
        assertEquals(0, gen.dataAt("order", "status").asInt(), "新订单应为待付款(0)");
        assertAmountEquals(expectedPay, gen.dataDecimal("order", "payAmount"), "订单应付");

        // 5. 下单后库存：lock_stock += qty，真实 stock 不变
        SkuStockFixture.SkuState afterOrder = SkuStockFixture.read(sku.skuId());
        assertEquals(before.lockStock() + qty, afterOrder.lockStock(), "下单应锁定库存 lock_stock+qty");
        assertEquals(before.stock(), afterOrder.stock(), "下单不应扣减真实库存");

        // 6. 支付成功回调
        Allure.step("支付 orderId=" + orderId, () -> assertSuccess(order.paySuccess(token, orderId, 1)));

        // 7. 支付后：订单 status=1 待发货；真实 stock -= qty；锁库存复原
        assertEquals(1, OrderFixture.status(orderId), "支付后订单应为待发货(1)");
        SkuStockFixture.SkuState afterPay = SkuStockFixture.read(sku.skuId());
        assertEquals(before.stock() - qty, afterPay.stock(), "支付应扣减真实库存 stock-qty");
        assertEquals(before.lockStock(), afterPay.lockStock(), "支付应释放锁定库存 lock_stock 复原");
    }
}
