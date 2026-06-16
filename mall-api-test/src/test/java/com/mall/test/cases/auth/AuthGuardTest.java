package com.mall.test.cases.auth;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.AdminClient;
import com.mall.test.client.MemberClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.core.ResultCode;
import com.mall.test.core.RestClient;
import com.mall.test.fixture.AdminFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.mall.test.core.ApiAssertions.assertCode;
import static com.mall.test.core.ApiAssertions.assertSuccess;

/**
 * P0 · 网关鉴权拦截：401（无/错 token、跨账号）与 403（越权）。
 * 口径来自实环境实测，见 docs/test-analysis-p0.md。
 */
@Epic("认证与鉴权")
@Feature("网关鉴权拦截")
class AuthGuardTest {

    private final AdminClient admin = new AdminClient();
    private final MemberClient member = new MemberClient();

    @Test
    @DisplayName("无 token 访问受保护会员接口 返回 401")
    void no_token_member_protected_returns_401() {
        ApiResponse r = ApiResponse.from(RestClient.given().get("/mall-portal/sso/info"));
        assertCode(r, ResultCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("错误 token 访问受保护会员接口 返回 401")
    void bad_token_member_protected_returns_401() {
        assertCode(member.info("not-a-real-token"), ResultCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("无 token 访问受保护后台接口 返回 401")
    void no_token_admin_protected_returns_401() {
        ApiResponse r = ApiResponse.from(RestClient.given().get("/mall-admin/admin/info"));
        assertCode(r, ResultCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("跨账号 会员 token 访问后台接口 返回 401")
    void member_token_on_admin_returns_401() {
        // 会员 token 不被 StpUtil 识别 -> 401（非 403），验证双账号隔离
        assertCode(admin.info(TokenFactory.memberToken()), ResultCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("越权 受限管理员访问无权接口 403 有权接口 200")
    void limited_admin_forbidden_returns_403() {
        AdminFixture.makeLimitedAdminLoginable();
        String token = TokenFactory.adminToken(AdminFixture.LIMITED_ADMIN_USERNAME, TestConfig.adminPassword());
        // 对照：有 /product/** 权限 -> 200
        assertSuccess(admin.productList(token));
        // 越权：缺 /order/** 权限 -> 403
        assertCode(admin.orderList(token), ResultCode.FORBIDDEN);
    }
}
