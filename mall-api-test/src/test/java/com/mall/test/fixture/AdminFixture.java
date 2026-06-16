package com.mall.test.fixture;

/**
 * 后台账号夹具。
 * 403 越权用例需要一个"可登录但权限受限"的管理员：种子里的 productAdmin（绑角色1=仅商品域）
 * 默认密码未知，这里把它的密码哈希改成与 admin 相同，使其可用 admin 的明文密码登录。
 * 幂等：每次设为 admin 当前哈希；测试环境无害，不做还原。
 */
public final class AdminFixture {

    public static final String LIMITED_ADMIN_USERNAME = "productAdmin"; // 角色1：商品域，无 /order/** 权限

    private AdminFixture() {}

    /** 使受限管理员 productAdmin 可用与 admin 相同的明文密码登录。返回其用户名。 */
    public static String makeLimitedAdminLoginable() {
        int n = Db.update(
                "UPDATE ums_admin SET password = " +
                "(SELECT p FROM (SELECT password p FROM ums_admin WHERE username = 'admin') t) " +
                "WHERE username = ?", LIMITED_ADMIN_USERNAME);
        if (n == 0) throw new IllegalStateException("未找到受限管理员 " + LIMITED_ADMIN_USERNAME + "（种子数据缺失？）");
        return LIMITED_ADMIN_USERNAME;
    }
}
