package com.mall.test.cases.coupon;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.AdminCouponClient;
import com.mall.test.client.MemberCouponClient;
import com.mall.test.core.ApiResponse;
import com.mall.test.core.KnownDefect;
import com.mall.test.fixture.CouponFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.mall.test.core.ApiAssertions.assertFailedWithMessage;
import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 · 营销券生命周期：后台 API 创建券，前台会员领取，验证总量与时间窗口。
 * 数据策略：券经 admin API 创建；teardown 按 couponId 清领取历史和券，避免污染共享会员。
 */
@Epic("优惠券营销")
@Feature("后台造券与领取生命周期")
@Story("admin 创建 / portal 领取 / 总量 / 时间窗")
@Owner("mall-qa")
@Severity(SeverityLevel.CRITICAL)
class CouponLifecycleAdminApiTest {

    private final AdminCouponClient adminCoupon = new AdminCouponClient();
    private final MemberCouponClient memberCoupon = new MemberCouponClient();
    private final String adminToken = TokenFactory.adminToken();
    private final String memberToken = TokenFactory.memberToken();
    private final List<Long> createdCouponIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long couponId : createdCouponIds) {
            CouponFixture.deleteManagedCoupon(couponId);
        }
        createdCouponIds.clear();
    }

    @Test
    @DisplayName("后台创建限量券 会员领取后库存归零 再领提示已领完")
    void admin_created_coupon_claim_then_sold_out() {
        long couponId = createCoupon("stock", 1, 2, LocalDateTime.now().minusMinutes(1));

        ApiResponse detail = adminCoupon.detail(adminToken, couponId);
        assertSuccess(detail);
        assertEquals(1, detail.dataAt("publishCount").asInt(), "发行数量应来自 admin 创建入参");
        assertEquals(1, CouponFixture.count(couponId), "创建后剩余数量应等于发行数量");
        assertEquals(0, CouponFixture.receiveCount(couponId), "创建后领取数应为 0");

        assertSuccess(memberCoupon.add(memberToken, couponId));
        assertEquals(0, CouponFixture.count(couponId), "领取后剩余数量应 -1");
        assertEquals(1, CouponFixture.receiveCount(couponId), "领取后 receive_count 应 +1");
        assertEquals(1, CouponFixture.historyCount(couponId), "领取后应产生一条历史记录");

        assertFailedWithMessage(memberCoupon.add(memberToken, couponId), "领完");
    }

    @Test
    @DisplayName("后台创建未来生效券 未到领取时间被拒")
    void future_enable_time_coupon_rejected_before_window() {
        long couponId = createCoupon("future", 3, 1, LocalDateTime.now().plusDays(1));

        assertFailedWithMessage(memberCoupon.add(memberToken, couponId), "还没到领取时间");
        assertEquals(3, CouponFixture.count(couponId), "未到领取时间不应扣减券库存");
        assertEquals(0, CouponFixture.receiveCount(couponId), "未到领取时间不应增加领取数");
        assertEquals(0, CouponFixture.historyCount(couponId), "未到领取时间不应产生领取历史");
    }

    @KnownDefect("R9: 优惠券并发领取无原子扣减，总量为 1 时可能产生多条领取历史")
    @Issue("R9")
    @Test
    @DisplayName("R9 并发领取限量券不应超发")
    void concurrent_claim_should_not_over_issue_coupon() throws Exception {
        long couponId = createCoupon("concurrent", 1, 99, LocalDateTime.now().minusMinutes(1));
        int workers = 12;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ApiResponse>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return memberCoupon.add(memberToken, couponId);
                }));
            }
            ready.await();
            go.countDown();

            int success = 0;
            for (Future<ApiResponse> future : futures) {
                if (future.get().isSuccess()) {
                    success++;
                }
            }

            assertTrue(success <= 1, "总量为 1 的券并发领取成功数不应超过 1，实际成功 " + success);
            assertEquals(0, CouponFixture.count(couponId), "并发领取后库存不应为负或重复扣减");
            assertEquals(1, CouponFixture.receiveCount(couponId), "receive_count 不应超过发行数量");
            assertEquals(1, CouponFixture.historyCount(couponId), "领取历史不应超过发行数量");
        } finally {
            pool.shutdownNow();
        }
    }

    private long createCoupon(String scenario, int publishCount, int perLimit, LocalDateTime enableTime) {
        String name = "TEST-ADMIN-COUPON-" + scenario + "-" + System.nanoTime();
        ApiResponse create = adminCoupon.create(adminToken,
                CouponFixture.universalCouponParam(name, new BigDecimal("12.00"), publishCount, perLimit, enableTime));
        assertSuccess(create);
        long couponId = CouponFixture.couponIdByName(name);
        createdCouponIds.add(couponId);
        return couponId;
    }
}
