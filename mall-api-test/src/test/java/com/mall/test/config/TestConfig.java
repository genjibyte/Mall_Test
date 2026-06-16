package com.mall.test.config;

import java.io.InputStream;
import java.util.Properties;

/**
 * 测试环境配置。加载 classpath:env.properties，并允许用环境变量覆盖
 * （键名转大写、'.'/'-' 换 '_'，如 gateway.base-url -> GATEWAY_BASE_URL）。
 */
public final class TestConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = TestConfig.class.getClassLoader().getResourceAsStream("env.properties")) {
            if (in == null) throw new IllegalStateException("env.properties not found on classpath");
            PROPS.load(in);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TestConfig() {}

    public static String get(String key) {
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        String fromSys = System.getProperty(key);
        if (fromSys != null && !fromSys.isBlank()) return fromSys;
        String v = PROPS.getProperty(key);
        if (v == null) throw new IllegalArgumentException("missing config key: " + key);
        return v;
    }

    public static String gatewayBaseUrl() { return get("gateway.base-url"); }
    public static String dbUrl()          { return get("db.url"); }
    public static String dbUsername()     { return get("db.username"); }
    public static String dbPassword()     { return get("db.password"); }
    public static String memberUsername() { return get("member.username"); }
    public static String memberPassword() { return get("member.password"); }
    public static String adminUsername()  { return get("admin.username"); }
    public static String adminPassword()  { return get("admin.password"); }
}
