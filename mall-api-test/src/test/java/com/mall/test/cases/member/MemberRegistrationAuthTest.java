package com.mall.test.cases.member;

import com.mall.test.client.AuthClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.MemberFixture;
import com.mall.test.fixture.RedisFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.mall.test.core.ApiAssertions.assertCode;
import static com.mall.test.core.ApiAssertions.assertFailedWithMessage;
import static com.mall.test.core.ApiAssertions.assertSuccess;
import static com.mall.test.core.ResultCode.FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 · 会员注册与认证安全：验证码 Redis 态、注册防重、改密后登录态切换。
 * 数据策略：只创建 autoreg_ 前缀会员，teardown 物理删除，避免污染种子账号。
 */
@Epic("会员中心")
@Feature("注册与认证安全")
@Story("验证码 / 注册防重 / 改密登录")
@Owner("mall-qa")
@Severity(SeverityLevel.CRITICAL)
class MemberRegistrationAuthTest {

    private static final String USERNAME_PREFIX = "autoreg_";
    private static final String PASSWORD = "Aa123456";
    private static final String NEW_PASSWORD = "Bb123456";

    private final AuthClient auth = new AuthClient();

    @AfterEach
    void cleanup() {
        MemberFixture.deleteAutoMembersByUsernamePrefix(USERNAME_PREFIX);
    }

    @Test
    @DisplayName("获取验证码 写入 Redis 且返回 6 位数字")
    void get_auth_code_writes_redis() {
        String telephone = uniqueTelephone();

        ApiResponse r = auth.getMemberAuthCode(telephone);

        assertSuccess(r);
        String authCode = r.data().asText();
        assertTrue(authCode.matches("\\d{6}"), () -> "验证码应为 6 位数字，实际: " + authCode);
        String key = TestConfig.authCodeCacheKey(telephone);
        assertEquals(authCode, redisTextValue(key), "接口返回验证码应与 Redis 中一致");
        assertTrue(RedisFixture.ttl(key) > 0, "验证码 Redis key 应有过期时间");
    }

    @Test
    @DisplayName("注册 错误验证码被拒 不落库")
    void register_with_wrong_auth_code_rejected() {
        String username = uniqueUsername("badcode");
        String telephone = uniqueTelephone();
        String realCode = authCode(telephone);
        String wrongCode = "000000".equals(realCode) ? "111111" : "000000";

        ApiResponse r = auth.registerMember(username, PASSWORD, telephone, wrongCode);

        assertFailedWithMessage(r, "验证码错误");
        assertFalse(MemberFixture.existsByUsername(username), "错码注册不应创建会员");
        assertFalse(MemberFixture.existsByPhone(telephone), "错码注册不应占用手机号");
    }

    @Test
    @DisplayName("注册成功 可登录 重复用户名或手机号被拒")
    void register_success_then_duplicate_rejected() {
        String username = uniqueUsername("ok");
        String telephone = uniqueTelephone();

        assertSuccess(auth.registerMember(username, PASSWORD, telephone, authCode(telephone)));

        assertTrue(MemberFixture.existsByUsername(username), "注册成功后应落库");
        assertTrue(MemberFixture.existsByPhone(telephone), "注册成功后应写入手机号");
        assertSuccess(auth.loginMember(username, PASSWORD));

        String duplicateUserPhone = uniqueTelephone();
        ApiResponse duplicateSameUser = auth.registerMember(
                username, PASSWORD, duplicateUserPhone, authCode(duplicateUserPhone));
        assertFailedWithMessage(duplicateSameUser, "存在");

        String anotherUsername = uniqueUsername("phone");
        ApiResponse duplicateSamePhone = auth.registerMember(anotherUsername, PASSWORD, telephone, authCode(telephone));
        assertFailedWithMessage(duplicateSamePhone, "存在");
    }

    @Test
    @DisplayName("修改密码 错码拒绝 正确码后旧密码失效新密码可登录")
    void update_password_switches_login_password() {
        String username = uniqueUsername("pwd");
        String telephone = uniqueTelephone();
        assertSuccess(auth.registerMember(username, PASSWORD, telephone, authCode(telephone)));
        String token = auth.loginMember(username, PASSWORD).dataText("token");

        String realCode = authCode(telephone);
        String wrongCode = "000000".equals(realCode) ? "111111" : "000000";
        assertFailedWithMessage(auth.updateMemberPassword(token, telephone, NEW_PASSWORD, wrongCode), "验证码错误");

        assertSuccess(auth.updateMemberPassword(token, telephone, NEW_PASSWORD, realCode));
        assertCode(auth.loginMember(username, PASSWORD), FAILED);
        assertSuccess(auth.loginMember(username, NEW_PASSWORD));
    }

    private String authCode(String telephone) {
        ApiResponse r = auth.getMemberAuthCode(telephone);
        assertSuccess(r);
        return r.data().asText();
    }

    private String uniqueUsername(String scenario) {
        return USERNAME_PREFIX + scenario + "_" + System.nanoTime();
    }

    private String uniqueTelephone() {
        String suffix = String.valueOf(System.nanoTime());
        suffix = suffix.substring(Math.max(0, suffix.length() - 8));
        return "139" + suffix;
    }

    private String redisTextValue(String key) {
        String value = RedisFixture.get(key);
        if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
