package com.mall.test.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记命中已知缺陷的用例：按"正确行为"断言（运行即失败=暴露缺陷）。
 * 默认 @Disabled 跳过，不阻断门禁（context-pack/05 QG5）；缺陷修复后移除本注解即可转为常驻守护。
 * value 写缺陷编号与简述，详见 context-pack/06-historical-badcases.md。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("KnownDefect")
@Disabled("KnownDefect：默认跳过，见 context-pack/06-historical-badcases.md")
public @interface KnownDefect {
    String value();
}
