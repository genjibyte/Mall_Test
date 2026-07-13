package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;

import java.util.Map;

/**
 * 后台优惠券管理接口客户端（/mall-admin/coupon/**）。需 admin token。
 */
public class AdminCouponClient {

    public ApiResponse create(String token, Map<String, Object> couponParam) {
        return ApiResponse.from(RestClient.givenAuth(token)
                .body(couponParam)
                .post("/mall-admin/coupon/create"));
    }

    public ApiResponse delete(String token, long couponId) {
        return ApiResponse.from(RestClient.givenAuth(token)
                .post("/mall-admin/coupon/delete/{id}", couponId));
    }

    public ApiResponse list(String token, String name, Integer type, int pageSize, int pageNum) {
        var req = RestClient.givenAuth(token)
                .queryParam("pageSize", pageSize)
                .queryParam("pageNum", pageNum);
        if (name != null) req = req.queryParam("name", name);
        if (type != null) req = req.queryParam("type", type);
        return ApiResponse.from(req.get("/mall-admin/coupon/list"));
    }

    public ApiResponse detail(String token, long couponId) {
        return ApiResponse.from(RestClient.givenAuth(token)
                .get("/mall-admin/coupon/{id}", couponId));
    }
}
