package com.mall.test.cases.admin;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.AdminClient;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.core.KnownDefect;
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
 * 后台管理链路 · 订单管理（管理员批量关闭订单）。
 * 绿用例验证管理员关单的状态机行为（status->4）；R8 探针暴露关单不释放锁定库存的缺陷。
 * 每个用例下一笔全新订单并在 teardown 复位 lock_stock，避免污染共享 SKU。
 */
@Epic("后台管理链路")
@Feature("订单关闭与状态")
class AdminOrderManagementTest {

    private final OrderClient order = new OrderClient();
    private final AdminClient admin = new AdminClient();

    private SkuStockFixture.OrderableSku sku;       // 本用例所用 SKU
    private SkuStockFixture.SkuState baseline;      // 下单前 lock_stock 基线，用于 teardown 复位

    @AfterEach
    void restoreLockStock() {
        if (sku != null && baseline != null) {
            // 管理员关单不释放锁库存（R8），统一在此复位，保证可重复运行。
            SkuStockFixture.setLockStock(sku.skuId(), baseline.lockStock());
        }
        sku = null;
        baseline = null;
    }

    /** 下一笔未付款订单（占用 lock_stock），返回 orderId，同时记录 SKU 与基线供断言/复位。 */
    private long placeUnpaidOrder(String memberToken, int qty) {
        long addressId = MemberFixture.defaultAddressId(MemberFixture.memberId(TestConfig.memberUsername()));
        sku = SkuStockFixture.findOrderableNoPromo(qty);
        baseline = SkuStockFixture.read(sku.skuId());
        OrderFlow.clearCart(memberToken);
        long cartId = OrderFlow.addToCart(memberToken, sku, qty);
        ApiResponse gen = order.generateOrder(memberToken, addressId, 1, List.of(cartId));
        assertSuccess(gen);
        return gen.dataLong("order", "id");
    }

    @Test
    @DisplayName("管理员批量关闭未付款订单 状态置已关闭(4)")
    void admin_close_unpaid_order_sets_status_closed() {
        String memberToken = TokenFactory.memberToken();
        String adminToken = TokenFactory.adminToken();
        long orderId = placeUnpaidOrder(memberToken, 1);

        assertSuccess(admin.closeOrders(adminToken, List.of(orderId), "自动化测试关闭"));
        assertEquals(4, OrderFixture.status(orderId), "管理员关闭后订单应为已关闭(4)");
    }

    @KnownDefect("R8: 管理员批量关单只置 status=4，不释放 lock_stock（与用户取消/超时不一致），造成库存泄漏")
    @Test
    @DisplayName("管理员关单应释放锁定库存(R8 缺陷探针)")
    void admin_close_should_release_locked_stock() {
        String memberToken = TokenFactory.memberToken();
        String adminToken = TokenFactory.adminToken();
        final int qty = 1;
        long orderId = placeUnpaidOrder(memberToken, qty);

        assertEquals(baseline.lockStock() + qty, SkuStockFixture.read(sku.skuId()).lockStock(),
                "下单应锁定库存 lock_stock += qty");

        assertSuccess(admin.closeOrders(adminToken, List.of(orderId), "自动化测试关闭"));

        // 正确行为：关单应释放锁定库存（与用户取消/超时一致）。实测未释放 -> R8 缺陷。
        assertEquals(baseline.lockStock(), SkuStockFixture.read(sku.skuId()).lockStock(),
                "管理员关单应释放锁定库存 lock_stock 复原（R8：实际未释放，库存泄漏）");
    }
}
