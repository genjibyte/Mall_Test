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

    /** 同 get，但缺失时返回默认值（用于可选配置，如 RabbitMQ 管理端，默认即本地部署值）。 */
    public static String getOrDefault(String key, String def) {
        try {
            return get(key);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public static String gatewayBaseUrl() { return get("gateway.base-url"); }
    public static String dbUrl()          { return get("db.url"); }
    public static String dbUsername()     { return get("db.username"); }
    public static String dbPassword()     { return get("db.password"); }
    public static String memberUsername() { return get("member.username"); }
    public static String memberPassword() { return get("member.password"); }
    public static String adminUsername()  { return get("admin.username"); }
    public static String adminPassword()  { return get("admin.password"); }
    public static String redisHost()      { return get("redis.host"); }
    public static int redisPort()         { return Integer.parseInt(get("redis.port")); }

    /** mall-portal 会员信息缓存的 Redis key：<prefix>:<memberId>。 */
    public static String memberCacheKey(long memberId) {
        return get("redis.member-key-prefix") + ":" + memberId;
    }

    // RabbitMQ 管理端（用于 MQ 延迟超时用例清队列），默认即本地部署值，可经 env 覆盖。
    public static String rabbitMgmtUrl()  { return getOrDefault("rabbit.mgmt-url", "http://localhost:15673"); }
    public static String rabbitUsername() { return getOrDefault("rabbit.username", "mall"); }
    public static String rabbitPassword() { return getOrDefault("rabbit.password", "mall"); }
    public static String rabbitVhost()    { return getOrDefault("rabbit.vhost", "/mall"); }
}
