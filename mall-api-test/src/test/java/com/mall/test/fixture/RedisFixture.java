package com.mall.test.fixture;

import com.mall.test.config.TestConfig;
import redis.clients.jedis.Jedis;

/**
 * Redis 灰盒辅助：失效缓存 / 读值。
 * 主要用途：失效 mall-portal 会员信息缓存，使 getCurrentMember 读最新 DB（积分用例）。
 */
public final class RedisFixture {

    private RedisFixture() {}

    public static void del(String key) {
        try (Jedis jedis = new Jedis(TestConfig.redisHost(), TestConfig.redisPort())) {
            jedis.del(key);
        }
    }

    public static String get(String key) {
        try (Jedis jedis = new Jedis(TestConfig.redisHost(), TestConfig.redisPort())) {
            return jedis.get(key);
        }
    }

    public static long ttl(String key) {
        try (Jedis jedis = new Jedis(TestConfig.redisHost(), TestConfig.redisPort())) {
            return jedis.ttl(key);
        }
    }
}
