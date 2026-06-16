package com.mall.test.core;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 断言助手。核心约束：**先断 body.code，再断 data**；金额用 BigDecimal.compareTo。
 * 见 context-pack/05-quality-gates.md QG1。
 */
public final class ApiAssertions {

    private ApiAssertions() {}

    /** 断言业务成功（code==200）。失败时打印完整响应。 */
    public static void assertSuccess(ApiResponse resp) {
        assertEquals(ResultCode.SUCCESS, resp.code(),
                () -> "期望 code=200，实际响应: " + resp);
    }

    /** 断言指定业务码（如 401/403/500）。 */
    public static void assertCode(ApiResponse resp, long expectedCode) {
        assertEquals(expectedCode, resp.code(),
                () -> "期望 code=" + expectedCode + "，实际响应: " + resp);
    }

    /** 断言业务失败（code==500）且 message 含指定片段。 */
    public static void assertFailedWithMessage(ApiResponse resp, String messagePart) {
        assertCode(resp, ResultCode.FAILED);
        assertTrue(resp.message() != null && resp.message().contains(messagePart),
                () -> "期望 message 含 '" + messagePart + "'，实际: " + resp);
    }

    /** 金额相等（忽略 scale，用 compareTo）。 */
    public static void assertAmountEquals(BigDecimal expected, BigDecimal actual, String desc) {
        assertTrue(expected.compareTo(actual) == 0,
                () -> desc + " 金额不符：期望 " + expected + "，实际 " + actual);
    }
}
