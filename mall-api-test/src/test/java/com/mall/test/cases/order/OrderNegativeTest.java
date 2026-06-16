package com.mall.test.cases.order;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.mall.test.core.ApiAssertions.assertFailedWithMessage;

/**
 * P1 · 下单负例：守门校验应以 code=500 + 明确文案拒绝（Asserts.fail）。
 * 文案取自源码 OmsPortalOrderServiceImpl；断言用 contains 规避全角标点。
 */
@Epic("下单主链路")
@Feature("下单负例")
class OrderNegativeTest {

    private final OrderClient order = new OrderClient();
    private final String token = TokenFactory.memberToken();

    @AfterEach
    void cleanup() {
        OrderFlow.clearCart(token);
    }

    @Test
    @DisplayName("缺收货地址 下单失败 提示请选择收货地址")
    void missing_address_should_fail() {
        // 收货地址校验在最前，cartIds 可为空
        Map<String, Object> param = Map.of("payType", 1, "cartIds", List.of());
        ApiResponse resp = order.generateOrder(token, param);
        assertFailedWithMessage(resp, "请选择收货地址");
    }

    @Test
    @DisplayName("库存不足 下单失败 提示库存不足")
    void out_of_stock_should_fail() {
        final int qty = 1;
        long addressId = MemberFixture.defaultAddressId(MemberFixture.memberId(TestConfig.memberUsername()));
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(qty);
        int originalLock = SkuStockFixture.read(sku.skuId()).lockStock();

        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, qty);
        // 制造缺货：lock_stock = stock => realStock = 0
        SkuStockFixture.setLockStock(sku.skuId(), sku.stock());
        try {
            ApiResponse resp = order.generateOrder(token, addressId, 1, List.of(cartId));
            assertFailedWithMessage(resp, "库存不足");
        } finally {
            // 还原锁定库存，避免污染后续用例
            SkuStockFixture.setLockStock(sku.skuId(), originalLock);
        }
    }
}
