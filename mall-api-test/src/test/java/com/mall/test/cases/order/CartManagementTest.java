package com.mall.test.cases.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.test.auth.TokenFactory;
import com.mall.test.client.CartClient;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P1 · 购物车管理：加购 → 改数量 → 删除。
 */
@Epic("下单主链路")
@Feature("购物车管理")
class CartManagementTest {

    private final CartClient cart = new CartClient();
    private final String token = TokenFactory.memberToken();

    @AfterEach
    void cleanup() {
        OrderFlow.clearCart(token);
    }

    @Test
    @DisplayName("加购-改数量-删除")
    void add_update_quantity_delete() {
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(1);
        OrderFlow.clearCart(token);

        // 加购，数量 1
        assertSuccess(cart.add(token, sku.productId(), sku.skuId(), 1,
                sku.price(), sku.skuCode(), sku.productName()));
        JsonNode item = findItem(sku.skuId());
        assertEquals(1, item.path("quantity").asInt(), "初始数量应为1");
        long cartItemId = item.path("id").asLong();

        // 改数量为 3
        assertSuccess(cart.updateQuantity(token, cartItemId, 3));
        assertEquals(3, findItem(sku.skuId()).path("quantity").asInt(), "数量应更新为3");

        // 删除
        assertSuccess(cart.deleteItems(token, List.of(cartItemId)));
        assertNull(findItemOrNull(sku.skuId()), "删除后购物车不应再有该商品");
    }

    private JsonNode findItem(long skuId) {
        JsonNode n = findItemOrNull(skuId);
        if (n == null) throw new AssertionError("购物车未找到 skuId=" + skuId);
        return n;
    }

    private JsonNode findItemOrNull(long skuId) {
        ApiResponse list = cart.list(token);
        assertSuccess(list);
        for (JsonNode item : list.data()) {
            if (item.path("productSkuId").asLong() == skuId) return item;
        }
        return null;
    }
}
