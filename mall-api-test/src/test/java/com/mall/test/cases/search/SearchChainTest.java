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

import java.time.Duration;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 · 商品搜索链路（#4）：DB→ES 导入、关键词检索、创建后可搜（异步）。
 * 搜索接口走网关白名单，无需 token。create 后 ES 有刷新延迟，用 Awaitility 轮询直到可见（QG3）。
 */
@Epic("商品搜索")
@Feature("ES 搜索链路")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchChainTest {

    private final SearchClient search = new SearchClient();

    @BeforeAll
    void importData() {
        assertSuccess(search.importAll()); // 确保 ES 有数据
    }

    @Test
    @DisplayName("导入全部商品到ES 返回数量")
    void import_all_returns_count() {
        ApiResponse r = search.importAll();
        assertSuccess(r);
        assertTrue(r.data().asInt() >= 0, () -> "导入数量应为非负，实际: " + r);
    }

    @Test
    @DisplayName("无关键词搜索 返回已导入商品")
    void search_all_returns_products() {
        ApiResponse r = search.searchSimple(null, 0, 5); // 省略 keyword -> 全部
        assertSuccess(r);
        assertTrue(r.dataAt("total").asInt() >= 1, () -> "应能搜到已导入商品，实际: " + r);
    }

    @Test
    @DisplayName("关键词P20 匹配到商品26")
    void keyword_search_matches_product() {
        ApiResponse r = search.searchSimple("P20", 0, 5);
        assertSuccess(r);
        assertTrue(r.dataAt("total").asInt() >= 1, () -> "P20 应匹配到商品，实际: " + r);
        assertEquals(26, r.dataAt("list").get(0).path("id").asInt(), "首条应为商品26(HUAWEI P20)");
    }

    @Test
    @DisplayName("商品写入ES后可被搜索 异步轮询")
    void create_then_searchable_async() {
        final long productId = 26;
        assertSuccess(search.deleteById(productId));
        assertSuccess(search.createById(productId));
        // ES 刷新延迟 -> 轮询直到可搜到该商品
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    ApiResponse r = search.searchSimple("P20", 0, 5);
                    if (!r.isSuccess()) return false;
                    for (JsonNode item : r.dataAt("list")) {
                        if (item.path("id").asLong() == productId) return true;
                    }
                    return false;
                });
    }
}
