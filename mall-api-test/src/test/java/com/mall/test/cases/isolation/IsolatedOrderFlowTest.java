package com.mall.test.cases.isolation;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.IsolatedSkuFixture;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测试基建 · 专用隔离数据下单（对照 audit H1 的“专用商品”治理方向）。
 * 在 IsolatedSkuFixture 提供的专属无促销大库存 SKU 上完成 下单→支付，
 * 断言库存的锁定/扣减只作用于该隔离 SKU，从不触碰共享目录 SKU —— 演示库存层隔离与可重复运行。
 * （会员级隔离——新建专用 member 承接订单累积——记为后续；本用例订单仍落在 test 会员。）
 */
@Epic("测试基建")
@Feature("专用隔离数据下单")
class IsolatedOrderFlowTest {

    private final OrderClient order = new OrderClient();

    @Test
    @DisplayName("隔离SKU上 下单→支付 库存增减仅作用于隔离SKU 可重复")
    void order_and_pay_on_isolated_sku() {
        String token = TokenFactory.memberToken();
        long addressId = MemberFixture.defaultAddressId(MemberFixture.memberId(TestConfig.memberUsername()));

        SkuStockFixture.OrderableSku sku = IsolatedSkuFixture.ensure();   // 满库存、锁定清零
        SkuStockFixture.SkuState before = SkuStockFixture.read(sku.skuId());

        OrderFlow.clearCart(token);
        long cartId = OrderFlow.addToCart(token, sku, 1);

        ApiResponse gen = order.generateOrder(token, addressId, 1, List.of(cartId));
        assertSuccess(gen);
        long orderId = gen.dataLong("order", "id");
        assertEquals(before.lockStock() + 1, SkuStockFixture.read(sku.skuId()).lockStock(),
                "下单应锁定隔离 SKU 库存");

        assertSuccess(order.paySuccess(token, orderId, 1));
        SkuStockFixture.SkuState afterPay = SkuStockFixture.read(sku.skuId());
        assertEquals(before.stock() - 1, afterPay.stock(), "支付应扣减隔离 SKU 真实库存");
        assertEquals(before.lockStock(), afterPay.lockStock(), "支付应释放锁定，lock_stock 复原");
    }
}
