package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 购物车接口客户端（/mall-portal/cart/**）。需 member token。
 */
public class CartClient {

    /**
     * 加购。必须带 price——无促销商品下单时金额计算直接用购物车价（price 为 null 会触发后端 NPE）。
     * skuCode/productName 为展示冗余字段。memberId 由 token 推断。
     */
    public ApiResponse add(String token, long productId, long productSkuId, int quantity,
                           BigDecimal price, String productSkuCode, String productName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("productSkuId", productSkuId);
        body.put("quantity", quantity);
        body.put("price", price);
        body.put("productSkuCode", productSkuCode);
        body.put("productName", productName);
        return ApiResponse.from(
                RestClient.givenAuth(token).body(body).post("/mall-portal/cart/add"));
    }

    /** 当前会员含促销的购物车列表（用于取 cartId / realStock / reduceAmount）。 */
    public ApiResponse listPromotion(String token) {
        return ApiResponse.from(
                RestClient.givenAuth(token).get("/mall-portal/cart/list/promotion"));
    }

    /** 清空购物车（用例隔离用）。 */
    public ApiResponse clear(String token) {
        return ApiResponse.from(
                RestClient.givenAuth(token).post("/mall-portal/cart/clear"));
    }
}
