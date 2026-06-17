package com.mall.test.fixture;

/**
 * 共享数据卫生：量化并修复测试累积的数据漂移（对照 audit H1）。
 * 安全清理动作仅限：复位负锁库存（数据损伤）、硬删软删购物车行（冗余，已逻辑删除）。
 * 报告类方法只读。维护用例 {@code DataMaintenanceTest} 调用修复；
 * 不变量守卫 {@code DataIntegrityTest} 复用只读方法。
 */
public final class DataHygieneFixture {

    private DataHygieneFixture() {}

    /** 负锁库存 SKU 数（lock_stock&lt;0 = 损伤，正常运行不应出现，多由 R1/R4 重复扣减造成）。 */
    public static int negativeLockStockCount() {
        return (int) Db.queryLong("SELECT COUNT(*) FROM pms_sku_stock WHERE lock_stock < 0");
    }

    /** 超锁 SKU 数（lock_stock&gt;stock = 损伤，realStock 为负）。 */
    public static int overLockedSkuCount() {
        return (int) Db.queryLong("SELECT COUNT(*) FROM pms_sku_stock WHERE lock_stock > stock");
    }

    /** 复位负锁库存为 0，返回修复行数。 */
    public static int resetNegativeLockStock() {
        return Db.update("UPDATE pms_sku_stock SET lock_stock = 0 WHERE lock_stock < 0");
    }

    /** 某会员的软删购物车行数（delete_status=1）。 */
    public static int softDeletedCartCount(long memberId) {
        return (int) Db.queryLong(
                "SELECT COUNT(*) FROM oms_cart_item WHERE member_id = ? AND delete_status = 1", memberId);
    }

    /** 硬删某会员的软删购物车行（已逻辑删除，物理清理冗余），返回删除行数。 */
    public static int purgeSoftDeletedCart(long memberId) {
        return Db.update("DELETE FROM oms_cart_item WHERE member_id = ? AND delete_status = 1", memberId);
    }

    /** 某会员滞留的未付款订单数（status=0, delete_status=0）—— 仅报告，不自动关闭（避免误伤业务态）。 */
    public static int openOrderCount(long memberId) {
        return (int) Db.queryLong(
                "SELECT COUNT(*) FROM oms_order WHERE member_id = ? AND status = 0 AND delete_status = 0", memberId);
    }

    /** 某会员的订单总数（任意状态）。 */
    public static int orderCount(long memberId) {
        return (int) Db.queryLong("SELECT COUNT(*) FROM oms_order WHERE member_id = ?", memberId);
    }

    /**
     * 物理清除某**一次性测试会员**的全部订单（含明细/操作记录），返回删除订单行数。
     * 仅用于专用隔离会员，禁止用于共享种子 test(id=1)。
     */
    public static int purgeMemberOrders(long memberId) {
        if (memberId <= 1) {
            throw new IllegalArgumentException("拒绝清除种子会员订单: memberId=" + memberId);
        }
        Db.update("DELETE oi FROM oms_order_item oi JOIN oms_order o ON oi.order_id = o.id WHERE o.member_id = ?", memberId);
        Db.update("DELETE oh FROM oms_order_operate_history oh JOIN oms_order o ON oh.order_id = o.id WHERE o.member_id = ?", memberId);
        return Db.update("DELETE FROM oms_order WHERE member_id = ?", memberId);
    }
}
