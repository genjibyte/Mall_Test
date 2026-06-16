package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单接口客户端（/mall-portal/order/**）。需 member token。
 */
public class OrderClient {

    /** 生成确认单（只读预览）。body = cartId 数组。 */
    public ApiResponse generateConfirmOrder(String token, List<Long> cartIds) {
        return ApiResponse.from(
                RestClient.givenAuth(token).body(cartIds).post("/mall-portal/order/generateConfirmOrder"));
    }

    /** 下单。OrderParam: memberReceiveAddressId/payType/cartIds[(+couponId/useIntegration)]。 */
    public ApiResponse generateOrder(String token, long addressId, int payType, List<Long> cartIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("memberReceiveAddressId", addressId);
        body.put("payType", payType);
        body.put("cartIds", cartIds);
        return ApiResponse.from(
                RestClient.givenAuth(token).body(body).post("/mall-portal/order/generateOrder"));
    }

    /** 支付成功回调。query: orderId, payType。 */
    public ApiResponse paySuccess(String token, long orderId, int payType) {
        return ApiResponse.from(
                RestClient.givenAuth(token)
                        .queryParam("orderId", orderId)
                        .queryParam("payType", payType)
                        .post("/mall-portal/order/paySuccess"));
    }

    public ApiResponse detail(String token, long orderId) {
        return ApiResponse.from(
                RestClient.givenAuth(token).get("/mall-portal/order/detail/{id}", orderId));
    }

    /** 用户取消订单（仅未付款）。 */
    public ApiResponse cancelUserOrder(String token, long orderId) {
        return ApiResponse.from(
                RestClient.givenAuth(token).queryParam("orderId", orderId).post("/mall-portal/order/cancelUserOrder"));
    }
}
