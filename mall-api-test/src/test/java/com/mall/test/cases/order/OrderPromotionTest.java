package com.mall.test.cases.order;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.PromotionFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.mall.test.core.ApiAssertions.assertAmountEquals;

/**
 * P1 · 促销金额：单品促销 / 满减 / 阶梯，断言订单 payAmount 与 DB 促销配置算得的预期一致。
 * 预期由 PromotionFixture 复刻 OmsPromotionServiceImpl 的算法从 DB 配置计算。
 * teardown 取消订单还原库存（不做支付）。
 */
@Epic("下单主链路")
@Feature("促销金额")
@Owner("mall-qa")
@Severity(SeverityLevel.CRITICAL)
class OrderPromotionTest {

    private final OrderClient order = new OrderClient();
    private final String token = TokenFactory.memberToken();
    private final long memberId = MemberFixture.memberId(TestConfig.memberUsername());
    private Long orderId;

    @AfterEach
    void cleanup() {
        if (orderId != null) order.cancelUserOrder(token, orderId);
        OrderFlow.clearCart(token);
        orderId = null;
    }

    @Test
    @DisplayName("单品促销 应付=促销价×数量")
    void single_promotion_payAmount() {
        final int qty = 1;
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableByPromotionType(1, qty);
        BigDecimal expected = PromotionFixture.singlePayAmount(sku.skuId(), qty);
        orderId = placeAndAssertPay(sku, qty, expected);
    }

    @Test
    @DisplayName("满减 应付=总价-满减额")
    void full_reduction_payAmount() {
        final int qty = 1; // 单行 qty=1 规避满减分摊的除法舍入
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableByPromotionType(4, qty);
        BigDecimal expected = PromotionFixture.fullReductionPayAmount(sku.productId(), sku.price(), qty);
        orderId = placeAndAssertPay(sku, qty, expected);
    }

    @Test
    @DisplayName("阶梯 满2件打8折 应付正确")
    void ladder_promotion_payAmount() {
        final int qty = 2; // 阶梯最低档 count=2
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableByPromotionType(3, qty);
        BigDecimal expected = PromotionFixture.ladderPayAmount(sku.productId(), sku.price(), qty);
        orderId = placeAndAssertPay(sku, qty, expected);
    }

    // --- helpers ---

    /** 加购下单（经 OrderFlow.placeOrder）并断言订单应付==expected，返回 orderId 供 teardown 取消。 */
    private long placeAndAssertPay(SkuStockFixture.OrderableSku sku, int qty, BigDecimal expected) {
        long addressId = MemberFixture.defaultAddressId(memberId);
        ApiResponse gen = OrderFlow.placeOrder(token, addressId, sku, qty);
        assertAmountEquals(expected, gen.dataDecimal("order", "payAmount"), "促销后应付");
        return gen.dataLong("order", "id");
    }
}
