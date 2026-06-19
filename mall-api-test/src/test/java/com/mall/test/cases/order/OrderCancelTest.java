package com.mall.test.cases.order;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.OrderFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1 · 取消回滚（库存）：未付款订单取消后 status=4，且锁定库存复原、真实库存不变。
 * 验证业务不变量 R5 的库存部分（券/积分回滚留待建券/积分夹具后补全）。
 */
@Epic("下单主链路")
@Feature("订单取消与回滚")
@Owner("mall-qa")
@Severity(SeverityLevel.CRITICAL)
class OrderCancelTest {

    private final OrderClient order = new OrderClient();

    @Test
    @DisplayName("取消未付款订单 状态置关闭 锁定库存复原")
    void cancel_unpaid_order_restores_lock_stock() {
        final int qty = 1;
        String token = TokenFactory.memberToken();
        long addressId = MemberFixture.defaultAddressId(MemberFixture.memberId(TestConfig.memberUsername()));
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(qty);
        SkuStockFixture.SkuState before = SkuStockFixture.read(sku.skuId());

        // 下单：lock_stock += qty，status=0
        ApiResponse gen = OrderFlow.placeOrder(token, addressId, sku, qty);
        long orderId = gen.dataLong("order", "id");
        SkuStockFixture.SkuState afterOrder = SkuStockFixture.read(sku.skuId());
        assertEquals(before.lockStock() + qty, afterOrder.lockStock(), "下单应锁定库存");

        // 取消未付款订单
        assertSuccess(order.cancelUserOrder(token, orderId));

        // 断言：status=4 已关闭；lock_stock 复原；真实 stock 始终未变
        assertEquals(4, OrderFixture.status(orderId), "取消后订单应为已关闭(4)");
        SkuStockFixture.SkuState afterCancel = SkuStockFixture.read(sku.skuId());
        assertEquals(before.lockStock(), afterCancel.lockStock(), "取消应释放锁定库存 lock_stock 复原");
        assertEquals(before.stock(), afterCancel.stock(), "未支付取消不应动真实库存");
    }
}
