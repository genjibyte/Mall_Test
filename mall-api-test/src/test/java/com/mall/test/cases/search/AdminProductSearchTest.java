package com.mall.test.cases.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.test.auth.TokenFactory;
import com.mall.test.client.AdminClient;
import com.mall.test.client.SearchClient;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.ProductFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.awaitility.Awaitility.await;

/**
 * 跨服务端到端：管理员建商品(mall-admin) -> 导入ES(mall-search) -> 可被搜索。
 * 验证「商品发布 → 搜索同步」全链路（链路 #4 完整形态）。
 */
@Epic("商品搜索")
@Feature("建品到可搜(跨服务)")
class AdminProductSearchTest {

    private final AdminClient admin = new AdminClient();
    private final SearchClient search = new SearchClient();

    private long productId = -1;

    @AfterEach
    void cleanup() {
        if (productId > 0) {
            search.deleteById(productId);        // 从 ES 移除
            ProductFixture.deleteProduct(productId); // 从 DB 移除
            productId = -1;
        }
    }

    @Test
    @DisplayName("管理员建商品后可被ES搜索到")
    void admin_created_product_becomes_searchable() {
        String unique = String.valueOf(System.currentTimeMillis());
        String name = "自动化测试商品" + unique;
        String sn = "ATSN" + unique;

        // 1. 管理员创建商品（上架）
        ApiResponse create = admin.createProduct(TokenFactory.adminToken(),
                ProductFixture.minimalProductParam(name, sn));
        assertSuccess(create);

        // 2. 取得新建商品 id，导入 ES
        productId = ProductFixture.productIdBySn(sn);
        assertSuccess(search.createById(productId));

        // 3. 轮询搜索直到能搜到该商品（ES 刷新延迟）
        final long pid = productId;
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    ApiResponse r = search.searchSimple("自动化测试商品", 0, 10);
                    if (!r.isSuccess()) return false;
                    for (JsonNode item : r.dataAt("list")) {
                        if (item.path("id").asLong() == pid) return true;
                    }
                    return false;
                });
    }
}
