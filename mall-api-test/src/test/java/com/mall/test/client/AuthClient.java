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

    public ApiResponse getMemberAuthCode(String telephone) {
        return ApiResponse.from(
                RestClient.given()
                        .queryParam("telephone", telephone)
                        .get("/mall-portal/sso/getAuthCode"));
    }

    public ApiResponse registerMember(String username, String password, String telephone, String authCode) {
        return ApiResponse.from(
                RestClient.given()
                        .contentType(ContentType.URLENC)
                        .formParam("username", username)
                        .formParam("password", password)
                        .formParam("telephone", telephone)
                        .formParam("authCode", authCode)
                        .post("/mall-portal/sso/register"));
    }

    public ApiResponse updateMemberPassword(String telephone, String password, String authCode) {
        return ApiResponse.from(
                RestClient.given()
                        .contentType(ContentType.URLENC)
                        .formParam("telephone", telephone)
                        .formParam("password", password)
                        .formParam("authCode", authCode)
                        .post("/mall-portal/sso/updatePassword"));
    }

    public ApiResponse updateMemberPassword(String token, String telephone, String password, String authCode) {
        return ApiResponse.from(
                RestClient.givenAuth(token)
                        .contentType(ContentType.URLENC)
                        .formParam("telephone", telephone)
                        .formParam("password", password)
                        .formParam("authCode", authCode)
                        .post("/mall-portal/sso/updatePassword"));
    }
}
