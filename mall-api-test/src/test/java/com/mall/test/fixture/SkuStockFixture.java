package com.mall.test.fixture;

import java.math.BigDecimal;
import java.util.Map;

/**
 * SKU 库存夹具。可用量 realStock = stock - lock_stock（见 context-pack/03）。
 */
public final class SkuStockFixture {

    private SkuStockFixture() {}

    /** 一个可下单的 SKU（含原价/库存/编码/品名，用于加购去重展示字段）。 */
    public record OrderableSku(long skuId, long productId, BigDecimal price, int stock, int lockStock,
                               String skuCode, String productName) {}

    /** SKU 当前库存状态（用于 before/after 差值断言）。 */
    public record SkuState(int stock, int lockStock) {}

    /**
     * 找一个**无促销(promotion_type=0)**、已上架、可用量>=needQty 的 SKU。
     * 选无促销是为了让金额断言保持简单：payAmount == price * qty。
     */
    public static OrderableSku findOrderableNoPromo(int needQty) {
        Map<String, Object> row = Db.queryRow(
                "SELECT s.id, s.product_id, s.price, s.stock, s.lock_stock, s.sku_code, p.name " +
                "FROM pms_sku_stock s JOIN pms_product p ON p.id = s.product_id " +
                "WHERE p.publish_status = 1 AND p.delete_status = 0 AND p.promotion_type = 0 " +
                "AND (s.stock - s.lock_stock) >= ? ORDER BY s.id LIMIT 1", needQty);
        if (row == null) throw new IllegalStateException("找不到可下单的无促销 SKU（库存/数据是否就绪？）");
        return new OrderableSku(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("product_id")).longValue(),
                new BigDecimal(row.get("price").toString()),
                ((Number) row.get("stock")).intValue(),
                ((Number) row.get("lock_stock")).intValue(),
                String.valueOf(row.get("sku_code")),
                String.valueOf(row.get("name")));
    }

    /** 找一个指定 promotion_type、已上架、可用量>=needQty 的 SKU（促销金额用例）。 */
    public static OrderableSku findOrderableByPromotionType(int promotionType, int needQty) {
        Map<String, Object> row = Db.queryRow(
                "SELECT s.id, s.product_id, s.price, s.stock, s.lock_stock, s.sku_code, p.name " +
                "FROM pms_sku_stock s JOIN pms_product p ON p.id = s.product_id " +
                "WHERE p.publish_status = 1 AND p.delete_status = 0 AND p.promotion_type = ? " +
                "AND (s.stock - s.lock_stock) >= ? ORDER BY s.id LIMIT 1", promotionType, needQty);
        if (row == null) throw new IllegalStateException("找不到 promotion_type=" + promotionType + " 的可下单 SKU");
        return new OrderableSku(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("product_id")).longValue(),
                new BigDecimal(row.get("price").toString()),
                ((Number) row.get("stock")).intValue(),
                ((Number) row.get("lock_stock")).intValue(),
                String.valueOf(row.get("sku_code")),
                String.valueOf(row.get("name")));
    }

    /** SKU 促销价（单品促销用）。 */
    public static BigDecimal promotionPrice(long skuId) {
        Map<String, Object> row = Db.queryRow("SELECT promotion_price FROM pms_sku_stock WHERE id = ?", skuId);
        return new BigDecimal(row.get("promotion_price").toString());
    }

    /** 直接设置锁定库存（负例：置 lock_stock=stock 使 realStock=0 触发"库存不足"）。 */
    public static void setLockStock(long skuId, int lockStock) {
        Db.update("UPDATE pms_sku_stock SET lock_stock = ? WHERE id = ?", lockStock, skuId);
    }

    public static SkuState read(long skuId) {
        Map<String, Object> row = Db.queryRow(
                "SELECT stock, lock_stock FROM pms_sku_stock WHERE id = ?", skuId);
        if (row == null) throw new IllegalStateException("sku not found: " + skuId);
        return new SkuState(((Number) row.get("stock")).intValue(),
                ((Number) row.get("lock_stock")).intValue());
    }
}
