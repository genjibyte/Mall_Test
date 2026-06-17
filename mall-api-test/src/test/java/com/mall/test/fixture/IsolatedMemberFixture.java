package com.mall.test.fixture;

import com.mall.test.auth.TokenFactory;

import java.util.Map;

/**
 * 专用隔离测试会员（对照 audit H1：让订单/购物车累积落在一次性会员上，而非共享种子 test）。
 * 幂等 ensure：不存在则克隆 test 的密码哈希与会员等级插入（可用 123456 登录），并保证有收货地址。
 * 与 {@link IsolatedSkuFixture} 配合即可完成会员+商品双隔离的下单链路。
 */
public final class IsolatedMemberFixture {

    private IsolatedMemberFixture() {}

    public static final String USERNAME = "isotest-mall";
    public static final String PASSWORD = "123456"; // 与 test 同哈希
    private static final String PHONE = "13900000001";

    /** 确保专用会员存在（含可登录、有地址），返回 memberId。 */
    public static synchronized long ensure() {
        Long id = existingId();
        if (id == null) {
            // 克隆 test 的 password(123456 哈希) 与 member_level_id，满足唯一约束与等级 FK，且可直接登录
            Db.update("INSERT INTO ums_member (username, password, phone, status, member_level_id, nickname, create_time, integration, growth) "
                            + "SELECT ?, password, ?, 1, member_level_id, ?, NOW(), 0, 0 FROM ums_member WHERE username = 'test'",
                    USERNAME, PHONE, USERNAME);
            id = existingId();
            if (id == null) {
                throw new IllegalStateException("专用隔离会员创建失败: " + USERNAME);
            }
        }
        AddressFixture.ensureAddress(id);
        return id;
    }

    /** 该专用会员的收货地址 id（下单用）。 */
    public static long addressId() {
        return AddressFixture.ensureAddress(ensure());
    }

    /** 该专用会员的会员 token（未缓存，独立于共享 test）。 */
    public static String token() {
        return TokenFactory.memberToken(USERNAME, PASSWORD);
    }

    private static Long existingId() {
        Map<String, Object> row = Db.queryRow("SELECT id FROM ums_member WHERE username = ?", USERNAME);
        return row == null ? null : ((Number) row.get("id")).longValue();
    }
}
