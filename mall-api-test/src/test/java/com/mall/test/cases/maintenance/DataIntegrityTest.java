package com.mall.test.cases.maintenance;

import com.mall.test.fixture.DataHygieneFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测试基建 · 共享库存数据完整性守卫（常驻绿色，随套件运行）。
 * 断言库存损伤类不变量：无负锁库存、无超锁(lock_stock>stock)。
 * 这些在正确业务运行中恒成立；若 R1/R4/R8 等缺陷在某次运行造成库存漂移，
 * 此守卫将失败、把 audit H1（数据漂移）暴露为可见门禁信号。损伤修复见 DataMaintenanceTest（手动）。
 */
@Epic("测试基建")
@Feature("库存数据完整性守卫")
class DataIntegrityTest {

    @Test
    @DisplayName("库存不变量：无负锁库存、无超锁 SKU")
    void stock_has_no_corruption() {
        assertEquals(0, DataHygieneFixture.negativeLockStockCount(),
                "存在 lock_stock<0 的 SKU（库存损伤；运行 DataMaintenanceTest 修复）");
        assertEquals(0, DataHygieneFixture.overLockedSkuCount(),
                "存在 lock_stock>stock 的 SKU（realStock 为负，库存损伤）");
    }
}
