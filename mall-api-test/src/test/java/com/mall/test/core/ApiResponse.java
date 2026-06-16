package com.mall.test.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;

import java.math.BigDecimal;

/**
 * mall 统一响应体 {code, message, data} 的封装。
 * data 保留为 Jackson JsonNode，便于按路径取值与类型断言。
 */
public final class ApiResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final long code;
    private final String message;
    private final JsonNode data;
    private final int httpStatus;
    private final String rawBody;

    private ApiResponse(long code, String message, JsonNode data, int httpStatus, String rawBody) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.httpStatus = httpStatus;
        this.rawBody = rawBody;
    }

    public static ApiResponse from(Response response) {
        String body = response.asString();
        try {
            JsonNode root = MAPPER.readTree(body);
            long code = root.path("code").asLong();
            String message = root.path("message").asText();
            JsonNode data = root.path("data");
            return new ApiResponse(code, message, data, response.statusCode(), body);
        } catch (Exception e) {
            throw new AssertionError("响应不是合法的 CommonResult JSON: " + body, e);
        }
    }

    public long code()        { return code; }
    public String message()   { return message; }
    public JsonNode data()    { return data; }
    public int httpStatus()   { return httpStatus; }
    public String rawBody()   { return rawBody; }

    public boolean isSuccess() { return code == ResultCode.SUCCESS; }

    /** 按路径取 data 子节点，如 dataAt("order","id")。 */
    public JsonNode dataAt(String... path) {
        JsonNode node = data;
        for (String p : path) node = node.path(p);
        return node;
    }

    public long dataLong(String... path)        { return dataAt(path).asLong(); }
    public String dataText(String... path)      { return dataAt(path).asText(); }
    public BigDecimal dataDecimal(String... p)  { return dataAt(p).decimalValue(); }

    @Override
    public String toString() {
        return "ApiResponse{http=" + httpStatus + ", code=" + code + ", message='" + message + "', data=" + data + '}';
    }
}
