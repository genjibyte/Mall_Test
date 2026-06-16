package com.mall.test.cases.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.test.auth.TokenFactory;
import com.mall.test.client.AdminClient;
import com.mall.test.client.MemberClient;
import com.mall.test.core.ApiResponse;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 · 认证 happy path：双账号登录拿 token，并能访问各自体系的受保护接口。
 */
@Epic("认证与鉴权")
@Feature("登录与令牌")
class AuthHappyPathTest {

    private final AdminClient admin = new AdminClient();
    private final MemberClient member = new MemberClient();

    @Test
    @DisplayName("管理员登录拿 token 可访问后台用户信息")
    void admin_login_can_access_info() {
        String token = TokenFactory.adminToken();
        ApiResponse info = admin.info(token);
        assertSuccess(info);
        boolean hasSuperRole = false;
        for (JsonNode role : info.dataAt("roles")) {
            if ("超级管理员".equals(role.asText())) hasSuperRole = true;
        }
        assertTrue(hasSuperRole, () -> "管理员应含'超级管理员'角色，实际: " + info);
    }

    @Test
    @DisplayName("会员登录拿 token 可访问会员信息")
    void member_login_can_access_info() {
        String token = TokenFactory.memberToken();
        assertSuccess(member.info(token));
    }
}
