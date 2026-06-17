package com.mall.test.cases.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.test.client.SearchClient;
import com.mall.test.core.ApiResponse;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商品搜索（#4）· 综合筛选与排序。只读用例（不改 ES/DB，零数据漂移）。
 * 采用属性型断言：品牌/分类的目标 id 从真实结果自取(不硬编码)，断言“筛选后全部命中”“价格单调”，
 * 对数据分布鲁棒。排序语义见 EsProductServiceImpl：3=price asc，4=price desc。
 */
@Epic("商品搜索")
@Feature("综合筛选与排序")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchFilterSortTest {

    private final SearchClient search = new SearchClient();

    @BeforeAll
    void importData() {
        assertSuccess(search.importAll()); // 确保 ES 有数据
    }

    @Test
    @DisplayName("按品牌筛选 结果全部命中该 brandId")
    void filter_by_brand_returns_only_that_brand() {
        long brandId = firstItem().path("brandId").asLong();

        ApiResponse filtered = search.search(null, brandId, null, 0, 50, 0);
        assertSuccess(filtered);
        JsonNode list = filtered.dataAt("list");
        assertTrue(list.size() > 0, "该品牌应有结果");
        for (JsonNode item : list) {
            assertEquals(brandId, item.path("brandId").asLong(),
                    "按品牌筛选后每条结果 brandId 必须等于 " + brandId);
        }
    }

    @Test
    @DisplayName("按分类筛选 结果全部命中该 productCategoryId")
    void filter_by_category_returns_only_that_category() {
        long categoryId = firstItem().path("productCategoryId").asLong();

        ApiResponse filtered = search.search(null, null, categoryId, 0, 50, 0);
        assertSuccess(filtered);
        JsonNode list = filtered.dataAt("list");
        assertTrue(list.size() > 0, "该分类应有结果");
        for (JsonNode item : list) {
            assertEquals(categoryId, item.path("productCategoryId").asLong(),
                    "按分类筛选后每条结果 productCategoryId 必须等于 " + categoryId);
        }
    }

    @Test
    @DisplayName("价格升序(sort=3) 结果价格非递减")
    void sort_by_price_ascending() {
        JsonNode list = pageList(search.search(null, null, null, 0, 50, 3));
        assertTrue(list.size() >= 2, "需至少 2 条以校验排序");
        BigDecimal prev = null;
        for (JsonNode item : list) {
            BigDecimal p = item.path("price").decimalValue();
            if (prev != null) {
                assertTrue(prev.compareTo(p) <= 0, "价格升序：前项 " + prev + " 应 <= 后项 " + p);
            }
            prev = p;
        }
    }

    @Test
    @DisplayName("价格降序(sort=4) 结果价格非递增")
    void sort_by_price_descending() {
        JsonNode list = pageList(search.search(null, null, null, 0, 50, 4));
        assertTrue(list.size() >= 2, "需至少 2 条以校验排序");
        BigDecimal prev = null;
        for (JsonNode item : list) {
            BigDecimal p = item.path("price").decimalValue();
            if (prev != null) {
                assertTrue(prev.compareTo(p) >= 0, "价格降序：前项 " + prev + " 应 >= 后项 " + p);
            }
            prev = p;
        }
    }

    /** 全量搜索的第一条（用于自取真实存在的 brandId/categoryId）。 */
    private JsonNode firstItem() {
        ApiResponse all = search.search(null, null, null, 0, 20, 0);
        assertSuccess(all);
        JsonNode list = all.dataAt("list");
        assertTrue(list.size() > 0, "索引应有商品");
        return list.get(0);
    }

    private JsonNode pageList(ApiResponse resp) {
        assertSuccess(resp);
        return resp.dataAt("list");
    }
}
