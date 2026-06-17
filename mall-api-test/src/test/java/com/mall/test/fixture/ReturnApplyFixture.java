package com.mall.test.fixture;

/**
 * 退货申请夹具：按 order_sn 取自建申请 id、读状态、清理。
 * 用例用唯一 order_sn 自建，teardown 删除，避免污染既有退货数据。
 */
public final class ReturnApplyFixture {

    private ReturnApplyFixture() {}

    public static long findIdByOrderSn(String orderSn) {
        return Db.queryLong(
                "SELECT id FROM oms_order_return_apply WHERE order_sn = ? ORDER BY id DESC LIMIT 1", orderSn);
    }

    /** 退货状态：0待处理/1确认退货/2完成退货/3拒绝退货。 */
    public static int status(long id) {
        return (int) Db.queryLong("SELECT status FROM oms_order_return_apply WHERE id = ?", id);
    }

    public static void delete(long id) {
        Db.update("DELETE FROM oms_order_return_apply WHERE id = ?", id);
    }
}
