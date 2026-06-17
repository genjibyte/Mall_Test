package com.mall.test.cases.defect;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.OrderClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.core.KnownDefect;
import com.mall.test.fixture.AddressFixture;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.SkuStockFixture;
import com.mall.test.flow.OrderFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缺陷探针：按"正确行为"断言，运行即失败=暴露缺陷。验证通过后将加 @KnownDefect 默认跳过。
 * 见 context-pack/06-historical-badcases.md（R1/R2）。
 */
@Epic("缺陷探针")
@Feature("订单支付缺陷")
class OrderDefectProbeTest {

    private final OrderClient order = new OrderClient();
    private final String token = TokenFactory.memberToken();
    private final long memberId = MemberFixture.memberId(TestConfig.memberUsername());

    @KnownDefect("R1: paySuccess 非幂等，重复支付重复扣库存（updateSkuStock 纯算术、无状态判定）")
    @Test
    @DisplayName("R1 paySuccess应幂等 重复支付只扣一次库存")
    void paySuccess_should_be_idempotent() {
        long addressId = MemberFixture.defaultAddressId(memberId);
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(1);
        SkuStockFixture.SkuState before = SkuStockFixture.read(sku.skuId());
        try {
            OrderFlow.clearCart(token);
            long cartId = OrderFlow.addToCart(token, sku, 1);
            long orderId = order.generateOrder(token, addressId, 1, List.of(cartId)).dataLong("order", "id");
            assertSuccess(order.paySuccess(token, orderId, 1));
            assertSuccess(order.paySuccess(token, orderId, 1)); // 重复支付
            assertEquals(before.stock() - 1, SkuStockFixture.read(sku.skuId()).stock(),
                    "重复支付库存应只扣一次");
        } finally {
            SkuStockFixture.setStock(sku.skuId(), before.stock());
            SkuStockFixture.setLockStock(sku.skuId(), before.lockStock());
        }
    }

    @KnownDefect("R2: paySuccess 无归属校验，任意会员可支付他人订单")
    @Test
    @DisplayName("R2 不能支付他人订单")
    void paySuccess_should_reject_non_owner() {
        long addressId = MemberFixture.defaultAddressId(memberId);
        String otherToken = TokenFactory.memberToken(
                MemberFixture.makeMemberLoginable(MemberFixture.SECOND_MEMBER_USERNAME), "123456");
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(1);
        SkuStockFixture.SkuState before = SkuStockFixture.read(sku.skuId());
        try {
            OrderFlow.clearCart(token);
            long cartId = OrderFlow.addToCart(token, sku, 1);
            long orderId = order.generateOrder(token, addressId, 1, List.of(cartId)).dataLong("order", "id");
            // 他人(windy)支付 test 的订单 -> 正确应被拒
            ApiResponse pay = order.paySuccess(otherToken, orderId, 1);
            assertFalse(pay.isSuccess(), "不应允许支付他人订单，实际: " + pay);
            order.cancelUserOrder(token, orderId); // 正确路径下未支付，取消还原
        } finally {
            SkuStockFixture.setStock(sku.skuId(), before.stock());
            SkuStockFixture.setLockStock(sku.skuId(), before.lockStock());
        }
    }

    @KnownDefect("R4: 并发下单无库存原子校验，超卖（lockStock 读改写、扣减 SQL 无卫语句、无乐观锁）")
    @Test
    @DisplayName("R4 并发下单不应超卖")
    void concurrent_orders_should_not_oversell() throws Exception {
        record Ctx(String token, long addr, long cartId) {}
        final int available = 2;
        List<String> usernames = List.of(MemberFixture.SECOND_MEMBER_USERNAME, "zhengsan", "lisi", "wangwu", "lion");
        SkuStockFixture.OrderableSku sku = SkuStockFixture.findOrderableNoPromo(1);
        SkuStockFixture.SkuState before = SkuStockFixture.read(sku.skuId());
        List<Long> created = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(usernames.size());
        try {
            // 准备：每个会员可登录 + 有地址 + 各自加购(顺序，避免购物车竞态)
            List<Ctx> ctxs = new ArrayList<>();
            for (String u : usernames) {
                MemberFixture.makeMemberLoginable(u);
                long mid = MemberFixture.memberId(u);
                long addr = AddressFixture.ensureAddress(mid);
                String tok = TokenFactory.memberToken(u, "123456");
                OrderFlow.clearCart(tok);
                ctxs.add(new Ctx(tok, addr, OrderFlow.addToCart(tok, sku, 1)));
            }
            // 设可用库存 = available
            SkuStockFixture.setStock(sku.skuId(), available);
            SkuStockFixture.setLockStock(sku.skuId(), 0);

            // 并发下单（同时释放）
            CountDownLatch ready = new CountDownLatch(ctxs.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<ApiResponse>> futures = new ArrayList<>();
            for (Ctx c : ctxs) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return order.generateOrder(c.token(), c.addr(), 1, List.of(c.cartId()));
                }));
            }
            ready.await();
            go.countDown();

            int success = 0;
            for (Future<ApiResponse> f : futures) {
                ApiResponse r = f.get();
                if (r.isSuccess()) { success++; created.add(r.dataLong("order", "id")); }
            }
            assertTrue(success <= available, "并发下单超卖：成功 " + success + " 单 > 可用库存 " + available);
        } finally {
            pool.shutdownNow();
            for (long oid : created) { try { order.cancelUserOrder(token, oid); } catch (Exception ignore) {} }
            SkuStockFixture.setStock(sku.skuId(), before.stock());
            SkuStockFixture.setLockStock(sku.skuId(), before.lockStock());
        }
    }
}
