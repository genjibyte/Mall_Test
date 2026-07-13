package com.mall.test.fixture;

import com.mall.test.config.TestConfig;

import java.util.Map;

/**
 * 会员相关夹具：id、默认收货地址、积分、缓存失效。
 */
public final class MemberFixture {

    private MemberFixture() {}

    /** 第二个会员（用于越权类用例）。种子存在但密码未知，需 makeMemberLoginable 后用 123456 登录。 */
    public static final String SECOND_MEMBER_USERNAME = "windy";

    /** 失效会员信息缓存，使下次 getCurrentMember 读最新 DB（积分用例必需）。 */
    public static void invalidateCache(long memberId) {
        RedisFixture.del(TestConfig.memberCacheKey(memberId));
    }

    /** 把指定会员的密码改成与 test 相同的哈希，使其可用 123456 登录。返回用户名。幂等。 */
    public static String makeMemberLoginable(String username) {
        int n = Db.update(
                "UPDATE ums_member SET password = " +
                "(SELECT p FROM (SELECT password p FROM ums_member WHERE username='test') t) " +
                "WHERE username = ?", username);
        if (n == 0) throw new IllegalStateException("未找到会员 " + username);
        return username;
    }

    public static long memberId(String username) {
        return Db.queryLong("SELECT id FROM ums_member WHERE username = ?", username);
    }

    public static boolean existsByUsername(String username) {
        return Db.queryLong("SELECT COUNT(*) FROM ums_member WHERE username = ?", username) > 0;
    }

    public static boolean existsByPhone(String phone) {
        return Db.queryLong("SELECT COUNT(*) FROM ums_member WHERE phone = ?", phone) > 0;
    }

    public static Long memberIdOrNull(String username) {
        Map<String, Object> row = Db.queryRow("SELECT id FROM ums_member WHERE username = ?", username);
        return row == null ? null : ((Number) row.get("id")).longValue();
    }

    /** 删除自动化注册会员。仅允许删除指定前缀，避免误伤种子会员。 */
    public static int deleteAutoMembersByUsernamePrefix(String usernamePrefix) {
        if (usernamePrefix == null || !usernamePrefix.startsWith("autoreg_")) {
            throw new IllegalArgumentException("拒绝删除非自动化注册会员前缀: " + usernamePrefix);
        }
        return Db.update("DELETE FROM ums_member WHERE username LIKE ?", usernamePrefix + "%");
    }

    /** 默认收货地址 id（优先 default_status=1）。 */
    public static long defaultAddressId(long memberId) {
        return Db.queryLong(
                "SELECT id FROM ums_member_receive_address WHERE member_id = ? " +
                "ORDER BY default_status DESC, id LIMIT 1", memberId);
    }

    public static int integration(long memberId) {
        return (int) Db.queryLong("SELECT integration FROM ums_member WHERE id = ?", memberId);
    }

    /** 设置会员积分（teardown 还原用）。设置后建议 invalidateCache。 */
    public static void setIntegration(long memberId, int value) {
        Db.update("UPDATE ums_member SET integration = ? WHERE id = ?", value, memberId);
    }
}
