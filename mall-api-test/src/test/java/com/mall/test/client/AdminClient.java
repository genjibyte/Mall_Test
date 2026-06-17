package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;
import io.restassured.http.ContentType;

import java.util.List;
import java.util.Map;

/**
 * 后台管理接口客户端（/mall-admin/**）。需 admin token（StpUtil 体系）。
 */
public class AdminClient {

    /** 创建商品。body = PmsProductParam(至少 name/productSn；列表字段可空)。返回 data=影响行数。 */
    public ApiResponse createProduct(String token, Map<String, Object> productParam) {
        return ApiResponse.from(RestClient.givenAuth(token)
                .body(productParam).post("/mall-admin/product/create"));
    }

    /** 批量发货（订单 status 1->2）。body = [{orderId,deliveryCompany,deliverySn}]。 */
    public ApiResponse deliver(String token, long orderId, String company, String sn) {
        return ApiResponse.from(RestClient.givenAuth(token)
                .body(List.of(Map.of("orderId", orderId, "deliveryCompany", company, "deliverySn", sn)))
                .post("/mall-admin/order/update/delivery"));
    }

    /** 批量上下架。form: ids(逗号分隔)/publishStatus(0下架/1上架)。纯 DB 更新 publish_status，无 ES 同步。 */
    public ApiResponse updatePublishStatus(String token, List<Long> ids, int publishStatus) {
        return ApiResponse.from(RestClient.givenAuth(token).contentType(ContentType.URLENC)
                .formParam("ids", csv(ids)).formParam("publishStatus", publishStatus)
                .post("/mall-admin/product/update/publishStatus"));
    }

    /** 批量设为新品。form: ids/newStatus(0/1)。 */
    public ApiResponse updateNewStatus(String token, List<Long> ids, int newStatus) {
        return ApiResponse.from(RestClient.givenAuth(token).contentType(ContentType.URLENC)
                .formParam("ids", csv(ids)).formParam("newStatus", newStatus)
                .post("/mall-admin/product/update/newStatus"));
    }

    /** 批量推荐商品。form: ids/recommendStatus(0/1)。 */
    public ApiResponse updateRecommendStatus(String token, List<Long> ids, int recommendStatus) {
        return ApiResponse.from(RestClient.givenAuth(token).contentType(ContentType.URLENC)
                .formParam("ids", csv(ids)).formParam("recommendStatus", recommendStatus)
                .post("/mall-admin/product/update/recommendStatus"));
    }

    /** 批量审核。form: ids/verifyStatus(0未审/1通过)/detail(审核意见)。 */
    public ApiResponse updateVerifyStatus(String token, List<Long> ids, int verifyStatus, String detail) {
        return ApiResponse.from(RestClient.givenAuth(token).contentType(ContentType.URLENC)
                .formParam("ids", csv(ids)).formParam("verifyStatus", verifyStatus).formParam("detail", detail)
                .post("/mall-admin/product/update/verifyStatus"));
    }

    /** 批量修改删除状态(软删/恢复)。form: ids/deleteStatus(0正常/1删除)。 */
    public ApiResponse updateDeleteStatus(String token, List<Long> ids, int deleteStatus) {
        return ApiResponse.from(RestClient.givenAuth(token).contentType(ContentType.URLENC)
                .formParam("ids", csv(ids)).formParam("deleteStatus", deleteStatus)
                .post("/mall-admin/product/update/deleteStatus"));
    }

    /** 批量关闭订单（status->4）。form: ids/note。注意：后端只置 status，不释放 lock_stock（见 R8）。 */
    public ApiResponse closeOrders(String token, List<Long> ids, String note) {
        return ApiResponse.from(RestClient.givenAuth(token).contentType(ContentType.URLENC)
                .formParam("ids", csv(ids)).formParam("note", note)
                .post("/mall-admin/order/update/close"));
    }

    /** 备注订单并改状态。form: id/note/status。 */
    public ApiResponse updateOrderNote(String token, long id, String note, int status) {
        return ApiResponse.from(RestClient.givenAuth(token).contentType(ContentType.URLENC)
                .formParam("id", id).formParam("note", note).formParam("status", status)
                .post("/mall-admin/order/update/note"));
    }

    private static String csv(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

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
