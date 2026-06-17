package com.mall.test.fixture;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 订单相关夹具：状态、应付金额等（用于副作用复核）。
 * 订单状态：0待付款 1待发货 2已发货 3已完成 4已关闭。
 */
public final class OrderFixture {

    private OrderFixture() {}

    public static int status(long orderId) {
        return (int) Db.queryLong("SELECT status FROM oms_order WHERE id = ?", orderId);
    }

    public static BigDecimal payAmount(long orderId) {
        Map<String, Object> row = Db.queryRow("SELECT pay_amount FROM oms_order WHERE id = ?", orderId);
        if (row == null) throw new IllegalStateException("order not found: " + orderId);
        return new BigDecimal(row.get("pay_amount").toString());
    }

    /** 把订单 create_time 回拨指定小时数（模拟超时，配合 cancelTimeOutOrder）。 */
    public static void backdateCreateTime(long orderId, int hours) {
        Db.update("UPDATE oms_order SET create_time = DATE_SUB(NOW(), INTERVAL ? HOUR) WHERE id = ?", hours, orderId);
    }
}
