package com.mall.test.support;

import com.mall.test.config.TestConfig;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * 企业级报告地基：测试会话启动时，把**被测环境信息**写入 Allure 结果目录的 environment.properties
 * （报告"Environment"面板），并把失败**分类规则** categories.json 复制进去（报告"Categories"页）。
 * 经 ServiceLoader 自动注册：META-INF/services/org.junit.platform.launcher.LauncherSessionListener。
 */
public class AllureEnvironmentListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        Path dir = Path.of(System.getProperty("allure.results.directory", "target/allure-results"));
        try {
            Files.createDirectories(dir);
            writeEnvironment(dir);
            copyCategories(dir);
        } catch (Exception e) {
            System.err.println("[allure] 写入 environment/categories 失败: " + e.getMessage());
        }
    }

    private void writeEnvironment(Path dir) throws IOException {
        StringBuilder sb = new StringBuilder();
        line(sb, "SUT", "mall-swarm (Spring Cloud Alibaba 2025 / Boot 3.5 / Sa-Token / Java 17)");
        line(sb, "Gateway", safe(TestConfig::gatewayBaseUrl));
        line(sb, "Database", safe(TestConfig::dbUrl));
        line(sb, "Redis", safe(() -> TestConfig.redisHost() + ":" + TestConfig.redisPort()));
        line(sb, "Framework", "JUnit5 + RestAssured + Allure");
        line(sb, "Java", System.getProperty("java.version", "?"));
        Files.writeString(dir.resolve("environment.properties"), sb.toString());
    }

    private void copyCategories(Path dir) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("allure/categories.json")) {
            if (in != null) {
                Files.copy(in, dir.resolve("categories.json"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void line(StringBuilder sb, String k, String v) {
        sb.append(k).append('=').append(v).append('\n');
    }

    private String safe(Supplier<String> v) {
        try {
            return v.get();
        } catch (Exception e) {
            return "?";
        }
    }
}
