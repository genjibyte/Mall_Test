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

    /** 下单（裸 body，负例用——可省略字段如不传 memberReceiveAddressId）。 */
    public ApiResponse generateOrder(String token, Map<String, Object> param) {
        return ApiResponse.from(
                RestClient.givenAuth(token).body(param).post("/mall-portal/order/generateOrder"));
    }

    /** 下单（无券/积分）。 */
    public ApiResponse generateOrder(String token, long addressId, int payType, List<Long> cartIds) {
        return generateOrder(token, addressId, payType, cartIds, null, null);
    }

    /** 下单（可带优惠券与积分）。couponId/useIntegration 为 null 则不传。 */
    public ApiResponse generateOrder(String token, long addressId, int payType, List<Long> cartIds,
                                     Long couponId, Integer useIntegration) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("memberReceiveAddressId", addressId);
        body.put("payType", payType);
        body.put("cartIds", cartIds);
        if (couponId != null) body.put("couponId", couponId);
        if (useIntegration != null) body.put("useIntegration", useIntegration);
        return generateOrder(token, body);
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

    /** 批量取消超时未付款订单（扫描 create_time 早于 normal_order_overtime 的 status=0 订单）。 */
    public ApiResponse cancelTimeOutOrder(String token) {
        return ApiResponse.from(
                RestClient.givenAuth(token).post("/mall-portal/order/cancelTimeOutOrder"));
    }
}
