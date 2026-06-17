package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;

/**
 * 前台商品浏览接口客户端（首页/商品详情/品牌/分类）。网关白名单，无需 token。
 */
public class PortalBrowseClient {

    public ApiResponse homeContent() {
        return ApiResponse.from(RestClient.given().get("/mall-portal/home/content"));
    }

    public ApiResponse productDetail(long productId) {
        return ApiResponse.from(RestClient.given().get("/mall-portal/product/detail/{id}", productId));
    }

    public ApiResponse categoryTreeList() {
        return ApiResponse.from(RestClient.given().get("/mall-portal/product/categoryTreeList"));
    }

    public ApiResponse brandRecommendList(int pageNum, int pageSize) {
        return ApiResponse.from(RestClient.given()
                .queryParam("pageNum", pageNum).queryParam("pageSize", pageSize)
                .get("/mall-portal/brand/recommendList"));
    }
}
