package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;

/**
 * 后台管理接口客户端（/mall-admin/**）。需 admin token（StpUtil 体系）。
 * 仅放置鉴权用例所需的少量受保护端点。
 */
public class AdminClient {

    /** 当前登录管理员信息（返回 roles/menus）。资源 31，多数角色可访问。 */
    public ApiResponse info(String token) {
        return ApiResponse.from(RestClient.givenAuth(token).get("/mall-admin/admin/info"));
    }

    /** 后台订单列表，需 /order/** 资源——用于 403 越权对照。 */
    public ApiResponse orderList(String token) {
        return ApiResponse.from(RestClient.givenAuth(token)
                .queryParam("pageNum", 1).queryParam("pageSize", 1)
                .get("/mall-admin/order/list"));
    }

    /** 后台商品列表，需 /product/** 资源——productAdmin 有权，用于 200 对照。 */
    public ApiResponse productList(String token) {
        return ApiResponse.from(RestClient.givenAuth(token)
                .queryParam("pageNum", 1).queryParam("pageSize", 1)
                .get("/mall-admin/product/list"));
    }
}
