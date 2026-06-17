package com.mall.test.cases.admin;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.AdminClient;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.ProductFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mall.test.core.ApiAssertions.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台管理链路 · 商品批量状态管理（上下架 / 新品 / 推荐）。
 * 完全自隔离：每个用例自建一个专属商品，断言 DB 状态后在 teardown 删除，
 * 不依赖也不污染共享种子数据（对照 audit H1，新增覆盖均做隔离）。
 */
@Epic("后台管理链路")
@Feature("商品批量状态管理")
class AdminProductManagementTest {

    private final AdminClient admin = new AdminClient();
    private String token;
    private long productId;

    @BeforeEach
    void setUp() {
        token = TokenFactory.adminToken();
        String sn = "TADM-" + System.currentTimeMillis();
        ApiResponse created = admin.createProduct(token, ProductFixture.minimalProductParam("管理端测试商品-" + sn, sn));
        assertSuccess(created);
        productId = ProductFixture.productIdBySn(sn);
        assertTrue(productId > 0, "创建后应能按货号查到商品 id");
    }

    @AfterEach
    void tearDown() {
        if (productId > 0) {
            ProductFixture.deleteProduct(productId);
        }
    }

    @Test
    @DisplayName("批量下架-上架 切换 publish_status")
    void batch_publish_status_toggles() {
        assertEquals(1, ProductFixture.publishStatus(productId), "创建后应为上架(1)");

        // 下架
        assertSuccess(admin.updatePublishStatus(token, List.of(productId), 0));
        assertEquals(0, ProductFixture.publishStatus(productId), "下架后 publish_status 应为 0");

        // 重新上架
        assertSuccess(admin.updatePublishStatus(token, List.of(productId), 1));
        assertEquals(1, ProductFixture.publishStatus(productId), "上架后 publish_status 应为 1");
    }

    @Test
    @DisplayName("批量设新品/推荐 切换 new_status 与 recommand_status")
    void batch_new_and_recommend_status() {
        assertEquals(0, ProductFixture.newStatus(productId), "初始非新品");
        assertEquals(0, ProductFixture.recommendStatus(productId), "初始非推荐");

        assertSuccess(admin.updateNewStatus(token, List.of(productId), 1));
        assertEquals(1, ProductFixture.newStatus(productId), "设为新品后 new_status=1");

        assertSuccess(admin.updateRecommendStatus(token, List.of(productId), 1));
        assertEquals(1, ProductFixture.recommendStatus(productId), "设为推荐后 recommand_status=1");
    }

    @Test
    @DisplayName("批量审核 切换 verify_status(含审核意见)")
    void batch_verify_status() {
        assertEquals(1, ProductFixture.verifyStatus(productId), "创建参数 verifyStatus=1");

        assertSuccess(admin.updateVerifyStatus(token, List.of(productId), 0, "驳回:信息不全"));
        assertEquals(0, ProductFixture.verifyStatus(productId), "驳回后 verify_status=0");

        assertSuccess(admin.updateVerifyStatus(token, List.of(productId), 1, "复审通过"));
        assertEquals(1, ProductFixture.verifyStatus(productId), "通过后 verify_status=1");
    }

    @Test
    @DisplayName("批量软删-恢复 切换 delete_status")
    void batch_delete_status_soft_delete_and_restore() {
        assertEquals(0, ProductFixture.deleteStatus(productId), "初始未删除");

        // 软删：仅置 delete_status=1，行仍在（teardown 仍可按 id 物理删除）
        assertSuccess(admin.updateDeleteStatus(token, List.of(productId), 1));
        assertEquals(1, ProductFixture.deleteStatus(productId), "软删后 delete_status=1");

        // 恢复
        assertSuccess(admin.updateDeleteStatus(token, List.of(productId), 0));
        assertEquals(0, ProductFixture.deleteStatus(productId), "恢复后 delete_status=0");
    }
}
