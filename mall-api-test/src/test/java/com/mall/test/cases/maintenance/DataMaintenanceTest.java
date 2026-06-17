package com.mall.test.cases.maintenance;

import com.mall.test.config.TestConfig;
import com.mall.test.fixture.DataHygieneFixture;
import com.mall.test.fixture.MemberFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测试基建 · 共享数据卫生维护（手动运行，默认 @Disabled，不计入常规门禁）。
 * 对照 audit H1：清理测试累积的数据漂移——复位负锁库存 + 硬删软删购物车行；滞留未付款订单仅报告。
 * 运行：mvn -Dtest=DataMaintenanceTest "-Djunit.jupiter.conditions.deactivate=*" test
 */
@Epic("测试基建")
@Feature("数据卫生维护")
@Tag("maintenance")
@Disabled("maintenance: 手动运行清理共享数据漂移，见类注释")
class DataMaintenanceTest {

    @Test
    @DisplayName("清理共享数据漂移：复位负锁库存 + 硬删软删购物车")
    void heal_shared_data_drift() {
        long memberId = MemberFixture.memberId(TestConfig.memberUsername());

        int negBefore = DataHygieneFixture.negativeLockStockCount();
        int overBefore = DataHygieneFixture.overLockedSkuCount();
        int cartBefore = DataHygieneFixture.softDeletedCartCount(memberId);
        int openOrders = DataHygieneFixture.openOrderCount(memberId);
        System.out.printf("[hygiene] before: negativeLockStock=%d overLocked=%d softDeletedCart=%d openOrders=%d%n",
                negBefore, overBefore, cartBefore, openOrders);

        int fixedLock = DataHygieneFixture.resetNegativeLockStock();
        int purgedCart = DataHygieneFixture.purgeSoftDeletedCart(memberId);
        System.out.printf("[hygiene] repaired: resetNegativeLockStock=%d purgedSoftDeletedCart=%d%n",
                fixedLock, purgedCart);

        int negAfter = DataHygieneFixture.negativeLockStockCount();
        int overAfter = DataHygieneFixture.overLockedSkuCount();
        int cartAfter = DataHygieneFixture.softDeletedCartCount(memberId);
        System.out.printf("[hygiene] after: negativeLockStock=%d overLocked=%d softDeletedCart=%d (滞留未付款订单=%d, 仅报告不自动关闭)%n",
                negAfter, overAfter, cartAfter, openOrders);

        assertEquals(0, negAfter, "复位后不应再有负锁库存");
        assertEquals(0, overAfter, "不应存在超锁 SKU");
        assertEquals(0, cartAfter, "清理后不应再有软删购物车行");
    }
}
