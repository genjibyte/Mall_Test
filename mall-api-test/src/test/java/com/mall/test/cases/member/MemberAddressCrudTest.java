package com.mall.test.cases.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.test.auth.TokenFactory;
import com.mall.test.client.MemberAddressClient;
import com.mall.test.config.TestConfig;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.AddressFixture;
import com.mall.test.fixture.MemberFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 · 会员收货地址 CRUD：新增 → 查询/详情 → 修改 → 删除 全链路。
 * 用非默认地址，避免影响下单用例依赖的默认地址(id=4)；teardown 兜底清理测试地址。
 */
@Epic("会员中心")
@Feature("收货地址CRUD")
class MemberAddressCrudTest {

    private static final String NAME_PREFIX = "自动化地址";
    private final MemberAddressClient addr = new MemberAddressClient();
    private final String token = TokenFactory.memberToken();
    private final long memberId = MemberFixture.memberId(TestConfig.memberUsername());

    @AfterEach
    void cleanup() {
        AddressFixture.deleteByNamePrefix(memberId, NAME_PREFIX);
    }

    @Test
    @DisplayName("收货地址 新增-详情-修改-删除")
    void address_full_crud() {
        String name = NAME_PREFIX + System.currentTimeMillis();

        // 新增
        assertSuccess(addr.add(token, address(name, "初始详细地址")));

        // 列表中按名称找到新增地址
        long id = findIdByName(name);
        assertTrue(id > 0, "新增后应能在列表中找到该地址");

        // 详情
        ApiResponse detail = addr.detail(token, id);
        assertSuccess(detail);
        assertEquals(name, detail.dataText("name"), "详情名称应一致");

        // 修改
        assertSuccess(addr.update(token, id, address(name, "更新后的详细地址")));
        assertEquals("更新后的详细地址", addr.detail(token, id).dataText("detailAddress"), "修改应生效");

        // 删除
        assertSuccess(addr.delete(token, id));
        assertNull(findIdByNameOrNull(name), "删除后列表中不应再有该地址");
    }

    // --- helpers ---

    private Map<String, Object> address(String name, String detailAddress) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", name);
        a.put("phoneNumber", "13900000000");
        a.put("defaultStatus", 0); // 非默认，避免影响下单默认地址
        a.put("postCode", "518000");
        a.put("province", "广东省");
        a.put("city", "深圳市");
        a.put("region", "南山区");
        a.put("detailAddress", detailAddress);
        return a;
    }

    private long findIdByName(String name) {
        Long id = findIdByNameOrNull(name);
        if (id == null) throw new AssertionError("列表中未找到地址: " + name);
        return id;
    }

    private Long findIdByNameOrNull(String name) {
        ApiResponse list = addr.list(token);
        assertSuccess(list);
        for (JsonNode item : list.data()) {
            if (name.equals(item.path("name").asText())) return item.path("id").asLong();
        }
        return null;
    }
}
