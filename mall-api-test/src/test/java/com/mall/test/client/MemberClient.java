package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;

/**
 * 会员中心接口客户端（/mall-portal/sso/**, 受保护部分）。需 member token（StpMemberUtil 体系）。
 */
public class MemberClient {

    /** 当前登录会员信息。受保护，用于认证 happy path。 */
    public ApiResponse info(String token) {
        return ApiResponse.from(RestClient.givenAuth(token).get("/mall-portal/sso/info"));
    }
}
