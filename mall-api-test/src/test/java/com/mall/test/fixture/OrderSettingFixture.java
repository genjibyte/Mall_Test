package com.mall.test.fixture;

/**
 * 订单设置夹具：读写 oms_order_setting（id=1）。
 * 主要用于把 normal_order_overtime 降到 1 分钟以触发 MQ 真实延迟超时，用后必须还原。
 */
public final class OrderSettingFixture {

    private OrderSettingFixture() {}

    /** 当前未付款订单超时分钟数 normal_order_overtime。 */
    public static int normalOrderOvertime() {
        return (int) Db.queryLong("SELECT normal_order_overtime FROM oms_order_setting WHERE id = 1");
    }

    /** 设置未付款订单超时分钟数（TTL 在下单发消息时按当前值计算，故须在下单前设置）。 */
    public static void setNormalOrderOvertime(int minutes) {
        Db.update("UPDATE oms_order_setting SET normal_order_overtime = ? WHERE id = 1", minutes);
    }
}
