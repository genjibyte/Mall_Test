package com.mall.test.fixture;

/**
 * 会员相关夹具：id、默认收货地址、积分。
 */
public final class MemberFixture {

    private MemberFixture() {}

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
}
