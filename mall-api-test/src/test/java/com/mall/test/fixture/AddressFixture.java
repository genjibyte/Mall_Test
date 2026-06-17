package com.mall.test.fixture;

import java.util.Map;

/**
 * 收货地址夹具。getItem 强校验地址归属，故每个下单会员都需自己的地址（并发超卖用例需多会员）。
 */
public final class AddressFixture {

    private AddressFixture() {}

    /** 清理某会员名称以 prefix 开头的测试地址（CRUD 用例兜底清理）。 */
    public static void deleteByNamePrefix(long memberId, String prefix) {
        Db.update("DELETE FROM ums_member_receive_address WHERE member_id = ? AND name LIKE ?",
                memberId, prefix + "%");
    }

    /** 返回该会员任一地址 id；没有则插入一条最小地址。 */
    public static long ensureAddress(long memberId) {
        Map<String, Object> row = Db.queryRow(
                "SELECT id FROM ums_member_receive_address WHERE member_id = ? ORDER BY id LIMIT 1", memberId);
        if (row != null) return ((Number) row.get("id")).longValue();
        return Db.insertReturnId(
                "INSERT INTO ums_member_receive_address(member_id,name,phone_number,default_status,post_code,province,city,region,detail_address) " +
                "VALUES(?,'测试收货人','13800000000',0,'000000','广东省','深圳市','南山区','测试详细地址')", memberId);
    }
}
