package com.mall.test.cases.order;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.AdminClient;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.OrderFixture;
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

/**
 * P1 · 订单全生命周期：会员下单 -> 支付 -> 管理员发货 -> 会员确认收货 -> 已完成。
 * 跨双账号(member+admin)，断言状态机 0->1->2->3。
 * 订单支付后无法回退，teardown 用 DB 还原库存保证可重复。
 */
@Epic("下单主链路")
@Feature("订单全生命周期")
class OrderLifecycleTest {

    private final OrderClient order = new OrderClient();
    private final AdminClient admin = new AdminClient();
    private final String memberToken = TokenFactory.memberToken();
    private final long memberId = MemberFixture.memberId(TestConfig.memberUsername());

    private long skuId;
    private SkuStockFixture.SkuState before;

    @AfterEach
    void restoreStock() {
        if (before != null) {
            SkuStockFixture.setStock(skuId, before.stock());
            SkuStockFixture.setLockStock(skuId, before.lockStock());
            before = null;
        }
        OrderFlow.clearCart(memberToken);
    }

    @Test
    @DisplayName("下单-支付-发货-确认收货 状态 0->1->2->3")
    void order_pay_ship_confirm_lifecycle() {
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(1);
        skuId = sku.skuId();
        before = SkuStockFixture.read(skuId);

        OrderFlow.clearCart(memberToken);
        long cartId = OrderFlow.addToCart(memberToken, sku, 1);
        ApiResponse gen = order.generateOrder(memberToken, addressId, 1, List.of(cartId));
        assertSuccess(gen);
        long orderId = gen.dataLong("order", "id");
        assertEquals(0, OrderFixture.status(orderId), "下单后待付款(0)");

        assertSuccess(order.paySuccess(memberToken, orderId, 1));
        assertEquals(1, OrderFixture.status(orderId), "支付后待发货(1)");

        // 管理员发货（需 /order/** 权限，用超管 token）
        assertSuccess(admin.deliver(TokenFactory.adminToken(), orderId, "顺丰", "SF" + orderId));
        assertEquals(2, OrderFixture.status(orderId), "发货后已发货(2)");

        // 会员确认收货
        assertSuccess(order.confirmReceiveOrder(memberToken, orderId));
        assertEquals(3, OrderFixture.status(orderId), "确认收货后已完成(3)");
    }
}
