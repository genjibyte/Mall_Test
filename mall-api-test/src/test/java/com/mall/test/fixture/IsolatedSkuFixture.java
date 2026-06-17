package com.mall.test.fixture;

import com.mall.test.auth.TokenFactory;
import com.mall.test.client.AdminClient;
import com.mall.test.core.ApiResponse;
import com.mall.test.fixture.SkuStockFixture.OrderableSku;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 专用隔离测试商品/SKU（对照 audit H1：避免在共享目录 SKU 上累积库存漂移）。
 * 幂等 ensure：按 sentinel 货号存在则复用、不存在则经 admin 建商品+SKU（无促销、大库存）。
 * 库存敏感的新用例可优先用它下单——锁定/扣减只作用于专属 SKU，且每次 ensure 复位为满库存、锁定清零。
 * 注：findOrderableNoPromo 取最小 id，本商品 id 最大，故不会被既有用例选中、互不干扰。
 */
public final class IsolatedSkuFixture {

    private IsolatedSkuFixture() {}

    public static final String PRODUCT_SN = "ISO-TEST-NOPROMO";
    private static final BigDecimal PRICE = new BigDecimal("99.00");
    private static final int FULL_STOCK = 100000;

    private static final AdminClient ADMIN = new AdminClient();

    /** 确保专用 SKU 存在并返回；每次复位为满库存、锁定清零，保证起点干净。 */
    public static synchronized OrderableSku ensure() {
        long productId = safeProductId();
        if (productId <= 0) {
            createProductWithSku();
            productId = ProductFixture.productIdBySn(PRODUCT_SN);
            if (productId <= 0) {
                throw new IllegalStateException("隔离商品创建后仍查不到货号: " + PRODUCT_SN);
            }
        }
        // 规范化 promotion_type=0：admin create 不写该字段会留 NULL，导致促销/下单计算 NPE。
        Db.update("UPDATE pms_product SET promotion_type = 0 WHERE id = ?", productId);
        Map<String, Object> sku = Db.queryRow(
                "SELECT id, sku_code, price, stock, lock_stock FROM pms_sku_stock WHERE product_id = ? ORDER BY id LIMIT 1",
                productId);
        if (sku == null) {
            throw new IllegalStateException("隔离商品无 SKU: productId=" + productId);
        }
        long skuId = ((Number) sku.get("id")).longValue();
        // 复位满库存、锁定清零
        Db.update("UPDATE pms_sku_stock SET stock = ?, lock_stock = 0 WHERE id = ?", FULL_STOCK, skuId);
        String skuCode = String.valueOf(sku.get("sku_code"));
        return new OrderableSku(skuId, productId, PRICE, FULL_STOCK, 0, skuCode, "隔离测试商品");
    }

    private static long safeProductId() {
        try {
            return ProductFixture.productIdBySn(PRODUCT_SN);
        } catch (RuntimeException noRow) {
            return -1;
        }
    }

    private static void createProductWithSku() {
        Map<String, Object> p = ProductFixture.minimalProductParam("隔离测试商品", PRODUCT_SN);
        p.put("price", PRICE);
        p.put("promotionType", 0); // 无促销，避免 promotion_type 为 NULL 引发计算 NPE
        Map<String, Object> sku = new LinkedHashMap<>();
        sku.put("price", PRICE);
        sku.put("stock", FULL_STOCK);
        sku.put("lockStock", 0);
        sku.put("lowStock", 0);
        p.put("skuStockList", List.of(sku)); // create 会 handleSkuStockCode + 插入 pms_sku_stock
        ApiResponse created = ADMIN.createProduct(TokenFactory.adminToken(), p);
        if (!created.isSuccess()) {
            throw new IllegalStateException("创建隔离商品失败: " + created);
        }
    }
}
