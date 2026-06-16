package com.mall.test.core;

import com.mall.test.config.TestConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * RestAssured 封装：统一 baseUri(网关) + JSON + Allure HTTP 抓取。
 * givenAuth 注入 Sa-Token 头 `Authorization: Bearer <token>`。
 */
public final class RestClient {

    private RestClient() {}

    public static RequestSpecification given() {
        return RestAssured.given()
                .baseUri(TestConfig.gatewayBaseUrl())
                .filter(new AllureRestAssured())
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    public static RequestSpecification givenAuth(String token) {
        return given().header("Authorization", "Bearer " + token);
    }
}
