package com.mall.test.fixture;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 促销金额预期计算（oracle），复刻 OmsPromotionServiceImpl 的 BigDecimal 运算，从 DB 读促销配置。
 * 仅覆盖能精确预期的形态：单品(qty 任意)、满减(单行 qty=1 规避除法舍入)、阶梯(qty>=count)。
 */
public final class PromotionFixture {

    private PromotionFixture() {}

    /** 单品促销：payAmount = promotion_price * qty。 */
    public static BigDecimal singlePayAmount(long skuId, int qty) {
        return SkuStockFixture.promotionPrice(skuId).multiply(BigDecimal.valueOf(qty));
    }

    /** 满减：total=price*qty；取 full_price<=total 中最大档的 reduce；payAmount = total - reduce（单行）。 */
    public static BigDecimal fullReductionPayAmount(long productId, BigDecimal price, int qty) {
        BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
        List<Map<String, Object>> rows = Db.queryList(
                "SELECT full_price, reduce_price FROM pms_product_full_reduction WHERE product_id = ? ORDER BY full_price DESC",
                productId);
        for (Map<String, Object> r : rows) {
            BigDecimal full = new BigDecimal(r.get("full_price").toString());
            if (total.compareTo(full) >= 0) {
                BigDecimal reduce = new BigDecimal(r.get("reduce_price").toString());
                return total.subtract(reduce);
            }
        }
        throw new IllegalStateException("无可用满减档：total=" + total + " product=" + productId);
    }

    /** 阶梯：取 count<=qty 中最大 count 的 discount；reduce=price-discount*price；payAmount=price*qty-reduce*qty。 */
    public static BigDecimal ladderPayAmount(long productId, BigDecimal price, int qty) {
        List<Map<String, Object>> rows = Db.queryList(
                "SELECT count, discount FROM pms_product_ladder WHERE product_id = ? ORDER BY count DESC", productId);
        BigDecimal q = BigDecimal.valueOf(qty);
        for (Map<String, Object> r : rows) {
            int count = ((Number) r.get("count")).intValue();
            if (qty >= count) {
                BigDecimal discount = new BigDecimal(r.get("discount").toString());
                BigDecimal reduce = price.subtract(discount.multiply(price));
                return price.multiply(q).subtract(reduce.multiply(q));
            }
        }
        throw new IllegalStateException("无可用阶梯档：qty=" + qty + " product=" + productId);
    }
}
