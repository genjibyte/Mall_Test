package com.mall.test.core;

/**
 * mall 统一响应业务码（CommonResult.code）。
 * 注意：这是 body 里的业务码，与 HTTP 状态码解耦——断言以此为准。
 * 来源：mall-common ResultCode.java。
 */
public final class ResultCode {
    public static final long SUCCESS = 200;          // 操作成功
    public static final long FAILED = 500;           // 操作失败（业务校验失败 Asserts.fail）
    public static final long VALIDATE_FAILED = 404;  // 参数校验失败
    public static final long UNAUTHORIZED = 401;     // 未登录/token 过期
    public static final long FORBIDDEN = 403;        // 无权限

    private ResultCode() {}
}
