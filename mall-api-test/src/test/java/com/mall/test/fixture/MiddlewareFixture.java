package com.mall.test.fixture;

import java.util.concurrent.TimeUnit;

/**
 * 中间件故障注入（可控混沌）：对指定容器 docker stop / start。
 * **仅供 @Tag("chaos") 手动用例**——会停掉共享中间件，绝不能进默认线；用例 teardown 必须强制恢复。
 * 边界：可控(docker 本地注入)、可复现(可 start 恢复)；不引入不可控外部依赖。
 */
public final class MiddlewareFixture {

    private MiddlewareFixture() {}

    public static final String REDIS = "mall-redis";
    public static final String RABBITMQ = "mall-rabbitmq";
    public static final String ES = "mall-elasticsearch";
    public static final String MYSQL = "mall-mysql";

    /** 停止容器（注入"依赖宕机"）。 */
    public static void stop(String container) {
        docker("stop", container);
    }

    /** 启动容器（恢复）。幂等：已在运行也无害。 */
    public static void start(String container) {
        docker("start", container);
    }

    /** 容器是否在运行（docker inspect Running）。 */
    public static boolean isRunning(String container) {
        try {
            Process p = new ProcessBuilder("docker", "inspect", "-f", "{{.State.Running}}", container)
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(15, TimeUnit.SECONDS);
            return out.contains("true");
        } catch (Exception e) {
            return false;
        }
    }

    private static void docker(String action, String container) {
        try {
            Process p = new ProcessBuilder("docker", action, container)
                    .redirectErrorStream(true).start();
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("docker " + action + " 超时: " + container);
            }
            if (p.exitValue() != 0) {
                String out = new String(p.getInputStream().readAllBytes()).trim();
                throw new IllegalStateException("docker " + action + " 失败(" + p.exitValue() + "): " + container + " | " + out);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("docker " + action + " 被中断: " + container, ie);
        } catch (Exception e) {
            throw new RuntimeException("docker " + action + " " + container + " 失败", e);
        }
    }
}
