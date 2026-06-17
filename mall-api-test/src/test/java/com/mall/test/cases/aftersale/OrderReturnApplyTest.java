package com.mall.test.cases.aftersale;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.ReturnApplyClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.ReturnApplyFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.mall.test.core.ApiAssertions.assertCode;
import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退货申请链路：会员申请(portal) → 后台处理(admin) 状态机 0→1确认/3拒绝；非法状态 no-op。
 * 自隔离：每用例用唯一 order_sn 自建申请，teardown 删除，不污染既有退货数据。
 */
@Epic("退货申请链路")
@Feature("会员申请 + 后台处理")
class OrderReturnApplyTest {

    private final ReturnApplyClient returns = new ReturnApplyClient();
    private long applyId = -1;

    @AfterEach
    void cleanup() {
        if (applyId > 0) {
            ReturnApplyFixture.delete(applyId);
        }
        applyId = -1;
    }

    /** 会员发起一笔退货申请，返回其 id（状态 0）。 */
    private long memberApply(String orderSn) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("orderSn", orderSn);
        p.put("memberUsername", TestConfig.memberUsername());
        p.put("productId", 26);
        p.put("productName", "退货测试商品");
        p.put("productCount", 1);
        p.put("productPrice", 99.00);
        p.put("returnName", "测试");
        p.put("returnPhone", "13800000000");
        p.put("reason", "测试退货");
        assertSuccess(returns.create(TokenFactory.memberToken(), p));
        long id = ReturnApplyFixture.findIdByOrderSn(orderSn);
        assertTrue(id > 0, "创建后应能按 order_sn 查到退货申请 id");
        return id;
    }

    @Test
    @DisplayName("会员申请 → 后台确认(0→1) 记录退款额")
    void member_apply_then_admin_approve() {
        String adminToken = TokenFactory.adminToken();
        applyId = memberApply("RT-APV-" + System.currentTimeMillis());
        assertEquals(0, ReturnApplyFixture.status(applyId), "新申请状态应为待处理(0)");

        // 后台可查详情
        assertSuccess(returns.adminDetail(adminToken, applyId));

        Map<String, Object> sp = new LinkedHashMap<>();
        sp.put("status", 1);
        sp.put("returnAmount", 99.00);
        sp.put("handleMan", "admin");
        sp.put("handleNote", "同意退货");
        assertSuccess(returns.adminUpdateStatus(adminToken, applyId, sp));
        assertEquals(1, ReturnApplyFixture.status(applyId), "确认后状态应为已确认退货(1)");
    }

    @Test
    @DisplayName("会员申请 → 后台拒绝(0→3)")
    void member_apply_then_admin_reject() {
        String adminToken = TokenFactory.adminToken();
        applyId = memberApply("RT-REJ-" + System.currentTimeMillis());

        Map<String, Object> sp = new LinkedHashMap<>();
        sp.put("status", 3);
        sp.put("handleMan", "admin");
        sp.put("handleNote", "不符合退货条件");
        assertSuccess(returns.adminUpdateStatus(adminToken, applyId, sp));
        assertEquals(3, ReturnApplyFixture.status(applyId), "拒绝后状态应为已拒绝退货(3)");
    }

    @Test
    @DisplayName("非法状态值 更新被拒(失败) 状态不变")
    void invalid_status_is_rejected() {
        String adminToken = TokenFactory.adminToken();
        applyId = memberApply("RT-INV-" + System.currentTimeMillis());

        Map<String, Object> sp = new LinkedHashMap<>();
        sp.put("status", 99); // 非 1/2/3 -> 后端 return 0 -> CommonResult.failed()
        ApiResponse r = returns.adminUpdateStatus(adminToken, applyId, sp);
        assertCode(r, 500);
        assertEquals(0, ReturnApplyFixture.status(applyId), "非法状态不应改变申请状态");
    }
}
