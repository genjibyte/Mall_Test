package com.mall.test.fixture;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品夹具：构造最小可创建商品参数、按货号查 id、清理。
 */
public final class ProductFixture {

    private ProductFixture() {}

    /** 最小商品创建参数：name/productSn + publishStatus=1 + 空列表（避免后端遍历 NPE）。 */
    public static Map<String, Object> minimalProductParam(String name, String productSn) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("productSn", productSn);
        p.put("publishStatus", 1);   // 上架，才会被 ES 导入
        p.put("verifyStatus", 1);
        p.put("deleteStatus", 0);
        p.put("newStatus", 0);
        p.put("recommandStatus", 0);
        p.put("price", 99.00);
        for (String listField : List.of("skuStockList", "memberPriceList", "productLadderList",
                "productFullReductionList", "productAttributeValueList",
                "subjectProductRelationList", "prefrenceAreaProductRelationList")) {
            p.put(listField, List.of());
        }
        return p;
    }

    public static long productIdBySn(String productSn) {
        return Db.queryLong("SELECT id FROM pms_product WHERE product_sn = ? ORDER BY id DESC LIMIT 1", productSn);
    }

    /** 删除测试商品（DB）。ES 侧用 SearchClient.deleteById 单独清理。 */
    public static void deleteProduct(long productId) {
        Db.update("DELETE FROM pms_product WHERE id = ?", productId);
    }
}
