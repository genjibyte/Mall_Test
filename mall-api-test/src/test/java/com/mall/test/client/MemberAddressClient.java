package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;

import java.util.Map;

/**
 * 会员收货地址接口客户端（/mall-portal/member/address/**）。需 member token。
 */
public class MemberAddressClient {

    public ApiResponse add(String token, Map<String, Object> address) {
        return ApiResponse.from(RestClient.givenAuth(token).body(address).post("/mall-portal/member/address/add"));
    }

    public ApiResponse list(String token) {
        return ApiResponse.from(RestClient.givenAuth(token).get("/mall-portal/member/address/list"));
    }

    public ApiResponse detail(String token, long id) {
        return ApiResponse.from(RestClient.givenAuth(token).get("/mall-portal/member/address/{id}", id));
    }

    public ApiResponse update(String token, long id, Map<String, Object> address) {
        return ApiResponse.from(RestClient.givenAuth(token).body(address).post("/mall-portal/member/address/update/{id}", id));
    }

    public ApiResponse delete(String token, long id) {
        return ApiResponse.from(RestClient.givenAuth(token).post("/mall-portal/member/address/delete/{id}", id));
    }
}
