package com.mall.test.fixture;

import com.mall.test.config.TestConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * RabbitMQ 管理端辅助：清空队列内容。
 * 用途：mall.order.cancel.ttl 是按消息 TTL 的延迟队列，存在“队头阻塞”——
 * 新发的短 TTL 消息排在既有长 TTL 消息之后不会按时死信。延迟超时用例下单前先清空它，保证确定性。
 */
public final class RabbitFixture {

    private RabbitFixture() {}

    /** 订单超时取消的 TTL 延迟队列。 */
    public static final String ORDER_CANCEL_TTL_QUEUE = "mall.order.cancel.ttl";

    /** 清空指定队列内容（DELETE /api/queues/{vhost}/{queue}/contents），返回是否 2xx。 */
    public static boolean purgeQueue(String queue) {
        try {
            String vhost = URLEncoder.encode(TestConfig.rabbitVhost(), StandardCharsets.UTF_8);
            String url = TestConfig.rabbitMgmtUrl() + "/api/queues/" + vhost + "/" + queue + "/contents";
            String auth = Base64.getEncoder().encodeToString(
                    (TestConfig.rabbitUsername() + ":" + TestConfig.rabbitPassword()).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Basic " + auth)
                    .DELETE()
                    .build();
            HttpResponse<String> resp = HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() / 100 == 2;
        } catch (Exception e) {
            throw new RuntimeException("purge RabbitMQ queue failed: " + queue, e);
        }
    }
}
