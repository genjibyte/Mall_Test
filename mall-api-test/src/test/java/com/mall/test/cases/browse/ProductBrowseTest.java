package com.mall.test.cases.browse;

import com.mall.test.client.PortalBrowseClient;
import com.mall.test.core.ApiResponse;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 · 前台商品浏览链路（公开，无 token）：首页内容、商品详情、分类树、推荐品牌。
 */
@Epic("商品浏览")
@Feature("前台浏览(公开)")
class ProductBrowseTest {

    private final PortalBrowseClient browse = new PortalBrowseClient();

    @Test
    @DisplayName("首页内容包含广告/品牌/热销商品")
    void home_content_has_sections() {
        ApiResponse r = browse.homeContent();
        assertSuccess(r);
        assertTrue(r.dataAt("advertiseList").isArray(), () -> "应含 advertiseList，实际: " + r);
        assertTrue(r.dataAt("brandList").isArray(), "应含 brandList");
        assertTrue(r.dataAt("hotProductList").isArray(), "应含 hotProductList");
    }

    @Test
    @DisplayName("商品详情返回对应商品")
    void product_detail_returns_product() {
        ApiResponse r = browse.productDetail(26);
        assertSuccess(r);
        assertEquals(26, r.dataAt("product", "id").asInt(), "详情应为商品26");
    }

    @Test
    @DisplayName("商品分类树非空")
    void category_tree_not_empty() {
        ApiResponse r = browse.categoryTreeList();
        assertSuccess(r);
        assertTrue(r.data().isArray() && r.data().size() >= 1, "分类树应非空");
    }

    @Test
    @DisplayName("推荐品牌列表返回数组")
    void brand_recommend_list_returns_array() {
        ApiResponse r = browse.brandRecommendList(1, 3);
        assertSuccess(r);
        assertTrue(r.data().isArray(), "推荐品牌应为数组");
    }
}
