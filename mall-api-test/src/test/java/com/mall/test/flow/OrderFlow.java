package com.mall.test.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.test.client.CartClient;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.SkuStockFixture.OrderableSku;

import static com.mall.test.core.ApiAssertions.assertSuccess;

/**
 * 业务流程层：编排下单链路中可复用的步骤，供各订单用例共享，避免重复。
 */
public final class OrderFlow {

    private static final CartClient CART = new CartClient();

    private OrderFlow() {}

    /** 清空当前会员购物车（用例隔离）。 */
    public static void clearCart(String token) {
        assertSuccess(CART.clear(token));
    }

    /** 把 SKU 加入购物车并返回购物车项 id（即下单用的 cartId）。 */
    public static long addToCart(String token, OrderableSku sku, int qty) {
        assertSuccess(CART.add(token, sku.productId(), sku.skuId(), qty,
                sku.price(), sku.skuCode(), sku.productName()));
        ApiResponse promo = CART.listPromotion(token);
        assertSuccess(promo);
        for (JsonNode item : promo.data()) {
            if (item.path("productSkuId").asLong() == sku.skuId()) {
                return item.path("id").asLong();
            }
        }
        throw new AssertionError("购物车中未找到 skuId=" + sku.skuId() + " 的条目: " + promo);
    }
}
