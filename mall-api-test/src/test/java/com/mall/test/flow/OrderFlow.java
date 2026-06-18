package com.mall.test.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.test.client.CartClient;
import com.mall.test.client.OrderClient;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.SkuStockFixture.OrderableSku;

import java.util.List;

import static com.mall.test.core.ApiAssertions.assertSuccess;

/**
 * 业务流程层：编排下单链路中可复用的步骤，供各订单用例共享，避免重复。
 */
public final class OrderFlow {

    private static final CartClient CART = new CartClient();
    private static final OrderClient ORDER = new OrderClient();

    private OrderFlow() {}

    /** 清空当前会员购物车（用例隔离，尽力而为）。
     *  注意：后端 /cart/clear 在无匹配行(空车)时返回 code 500，故此处不断言成功。 */
    public static void clearCart(String token) {
        CART.clear(token);
    }

    /**
     * 加购并下单（无券/积分、payType=1）：清车 → 加购 → generateOrder，断言成功后返回下单响应。
     * 调用方从返回体取 {@code dataLong("order","id")} 或校验 order.payAmount/status。
     * 消除各下单用例重复的"清车→加购→下单→断言"样板。
     */
    public static ApiResponse placeOrder(String token, long addressId, OrderableSku sku, int qty) {
        clearCart(token);
        long cartId = addToCart(token, sku, qty);
        ApiResponse gen = ORDER.generateOrder(token, addressId, 1, List.of(cartId));
        assertSuccess(gen);
        return gen;
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
