package com.mall.test.fixture;

import com.mall.test.config.TestConfig;

/**
 * 会员相关夹具：id、默认收货地址、积分、缓存失效。
 */
public final class MemberFixture {

    private MemberFixture() {}

    /** 失效会员信息缓存，使下次 getCurrentMember 读最新 DB（积分用例必需）。 */
    public static void invalidateCache(long memberId) {
        RedisFixture.del(TestConfig.memberCacheKey(memberId));
    }

    public static long memberId(String username) {
        return Db.queryLong("SELECT id FROM ums_member WHERE username = ?", username);
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
