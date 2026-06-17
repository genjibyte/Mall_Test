package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;
import io.restassured.specification.RequestSpecification;

/**
 * 会员优惠券接口客户端（/mall-portal/member/coupon/**）。需 member token。
 */
public class MemberCouponClient {

    /** 领取指定优惠券。 */
    public ApiResponse add(String token, long couponId) {
        return ApiResponse.from(
                RestClient.givenAuth(token).post("/mall-portal/member/coupon/add/{id}", couponId));
    }

    /** 会员优惠券领取历史（可按使用状态过滤）。 */
    public ApiResponse listHistory(String token, Integer useStatus) {
        RequestSpecification req = RestClient.givenAuth(token);
        if (useStatus != null) req = req.queryParam("useStatus", useStatus);
        return ApiResponse.from(req.get("/mall-portal/member/coupon/listHistory"));
    }
}
