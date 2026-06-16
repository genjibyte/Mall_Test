package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;
import io.restassured.http.ContentType;

import java.util.Map;

/**
 * 认证接口客户端。
 * 管理员：POST /mall-admin/admin/login (JSON body)；会员：POST /mall-portal/sso/login (form)。
 * 两套账号体系，返回各自的 Sa-Token（data.token）。
 */
public class AuthClient {

    public ApiResponse loginAdmin(String username, String password) {
        return ApiResponse.from(
                RestClient.given()
                        .body(Map.of("username", username, "password", password))
                        .post("/mall-admin/admin/login"));
    }

    public ApiResponse loginMember(String username, String password) {
        return ApiResponse.from(
                RestClient.given()
                        .contentType(ContentType.URLENC)
                        .formParam("username", username)
                        .formParam("password", password)
                        .post("/mall-portal/sso/login"));
    }
}
