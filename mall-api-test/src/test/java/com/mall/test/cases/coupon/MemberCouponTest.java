package com.mall.test.cases.coupon;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.MemberCouponClient;
import com.mall.test.config.TestConfig;
import com.mall.test.fixture.CouponFixture;
import com.mall.test.fixture.MemberFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.mall.test.core.ApiAssertions.assertFailedWithMessage;
import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1 · 优惠券营销链路(#5)：会员领取优惠券，per_limit 限制重复领取。
 * 用 DB 夹具建一张可领取的全场券(per_limit=1)，经 /member/coupon/add 领取。
 */
@Epic("优惠券营销")
@Feature("领取优惠券")
class MemberCouponTest {

    private final MemberCouponClient coupon = new MemberCouponClient();
    private final String token = TokenFactory.memberToken();
    private final long memberId = MemberFixture.memberId(TestConfig.memberUsername());
    private CouponFixture.TestCoupon tc;

    @AfterEach
    void cleanup() {
        if (tc != null) { CouponFixture.delete(tc); tc = null; }
    }

    @Test
    @DisplayName("领取优惠券 重复领取被拒(per_limit)")
    void claim_coupon_then_duplicate_rejected() {
        tc = CouponFixture.createUniversalCouponForClaim(new BigDecimal("15.00"));

        // 首次领取成功，产生未使用记录
        assertSuccess(coupon.add(token, tc.couponId()));
        assertEquals(0, CouponFixture.historyUseStatus(tc.couponId(), memberId), "领取后应有未使用(0)记录");

        // per_limit=1，重复领取被拒
        assertFailedWithMessage(coupon.add(token, tc.couponId()), "领取过");
    }
}
