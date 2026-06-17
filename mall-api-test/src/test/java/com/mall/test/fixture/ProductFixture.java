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

    /** 上架状态 publish_status（0下架/1上架）。 */
    public static int publishStatus(long productId) {
        return (int) Db.queryLong("SELECT publish_status FROM pms_product WHERE id = ?", productId);
    }

    /** 新品状态 new_status（0/1）。 */
    public static int newStatus(long productId) {
        return (int) Db.queryLong("SELECT new_status FROM pms_product WHERE id = ?", productId);
    }

    /** 推荐状态 recommand_status（注意：DB 列名为 recommand，源码拼写）。 */
    public static int recommendStatus(long productId) {
        return (int) Db.queryLong("SELECT recommand_status FROM pms_product WHERE id = ?", productId);
    }

    /** 审核状态 verify_status（0未审核/1审核通过）。 */
    public static int verifyStatus(long productId) {
        return (int) Db.queryLong("SELECT verify_status FROM pms_product WHERE id = ?", productId);
    }

    /** 删除状态 delete_status（0正常/1已删除）。 */
    public static int deleteStatus(long productId) {
        return (int) Db.queryLong("SELECT delete_status FROM pms_product WHERE id = ?", productId);
    }

    /** 删除测试商品（DB）。ES 侧用 SearchClient.deleteById 单独清理。 */
    public static void deleteProduct(long productId) {
        Db.update("DELETE FROM pms_product WHERE id = ?", productId);
    }
}
