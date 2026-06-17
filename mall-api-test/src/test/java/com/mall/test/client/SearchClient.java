package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;
import io.restassured.specification.RequestSpecification;

/**
 * 商品搜索接口客户端（/mall-search/esProduct/**）。网关白名单，无需 token。
 * 注意：keyword 省略(null) -> 返回全部；keyword="" -> 匹配 0 条。
 */
public class SearchClient {

    public ApiResponse importAll() {
        return ApiResponse.from(RestClient.given().post("/mall-search/esProduct/importAll"));
    }

    public ApiResponse searchSimple(String keyword, int pageNum, int pageSize) {
        RequestSpecification req = RestClient.given()
                .queryParam("pageNum", pageNum).queryParam("pageSize", pageSize);
        if (keyword != null) req = req.queryParam("keyword", keyword);
        return ApiResponse.from(req.get("/mall-search/esProduct/search/simple"));
    }

    /** 把数据库商品按 id 写入 ES。 */
    public ApiResponse createById(long id) {
        return ApiResponse.from(RestClient.given().post("/mall-search/esProduct/create/{id}", id));
    }

    /** 从 ES 按 id 删除。 */
    public ApiResponse deleteById(long id) {
        return ApiResponse.from(RestClient.given().get("/mall-search/esProduct/delete/{id}", id));
    }
}
