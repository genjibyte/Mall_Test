package com.mall.test.client;

import com.mall.test.core.ApiResponse;
import com.mall.test.core.RestClient;
import io.restassured.http.ContentType;

import java.util.List;
import java.util.Map;

/**
 * 退货申请：会员申请(/mall-portal/returnApply) + 后台处理(/mall-admin/returnApply)。
 * 注意：portal create 需会员 token（白名单不含该路径），且 memberUsername 取自 body 而非 token。
 */
public class ReturnApplyClient {

    /** 会员申请退货。body = OmsOrderReturnApplyParam。 */
    public ApiResponse create(String memberToken, Map<String, Object> param) {
        return ApiResponse.from(RestClient.givenAuth(memberToken).body(param)
                .post("/mall-portal/returnApply/create"));
    }

    /** 后台分页查询退货申请。 */
    public ApiResponse adminList(String adminToken, int pageNum, int pageSize) {
        return ApiResponse.from(RestClient.givenAuth(adminToken)
                .queryParam("pageNum", pageNum).queryParam("pageSize", pageSize)
                .get("/mall-admin/returnApply/list"));
    }

    /** 后台退货申请详情。 */
    public ApiResponse adminDetail(String adminToken, long id) {
        return ApiResponse.from(RestClient.givenAuth(adminToken).get("/mall-admin/returnApply/{id}", id));
    }

    /** 后台修改状态。body = OmsUpdateStatusParam（status:1确认/2完成/3拒绝；非法值后端 no-op 返回失败）。 */
    public ApiResponse adminUpdateStatus(String adminToken, long id, Map<String, Object> statusParam) {
        return ApiResponse.from(RestClient.givenAuth(adminToken).body(statusParam)
                .post("/mall-admin/returnApply/update/status/{id}", id));
    }

    /** 后台批量删除退货申请。form ids。 */
    public ApiResponse adminDelete(String adminToken, List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        return ApiResponse.from(RestClient.givenAuth(adminToken).contentType(ContentType.URLENC)
                .formParam("ids", sb.toString()).post("/mall-admin/returnApply/delete"));
    }
}
