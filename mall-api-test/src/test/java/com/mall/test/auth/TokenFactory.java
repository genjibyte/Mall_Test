package com.mall.test.auth;

import com.mall.test.client.AuthClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiAssertions;
import com.mall.test.core.ApiResponse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 双账号 token 工厂：缓存 member/admin 的 Sa-Token，避免每个用例重复登录。
 * 两套体系互不通用。
 */
public final class TokenFactory {

    private static final ConcurrentMap<String, String> CACHE = new ConcurrentHashMap<>();
    private static final AuthClient AUTH = new AuthClient();

    private TokenFactory() {}

    public static String memberToken() {
        return CACHE.computeIfAbsent("member", k -> {
            ApiResponse r = AUTH.loginMember(TestConfig.memberUsername(), TestConfig.memberPassword());
            ApiAssertions.assertSuccess(r);
            return r.dataText("token");
        });
    }

    public static String adminToken() {
        return CACHE.computeIfAbsent("admin", k -> {
            ApiResponse r = AUTH.loginAdmin(TestConfig.adminUsername(), TestConfig.adminPassword());
            ApiAssertions.assertSuccess(r);
            return r.dataText("token");
        });
    }

    /** 用指定凭据登录拿 token（用于越权/多账号用例），不缓存。 */
    public static String memberToken(String username, String password) {
        ApiResponse r = AUTH.loginMember(username, password);
        ApiAssertions.assertSuccess(r);
        return r.dataText("token");
    }
}
