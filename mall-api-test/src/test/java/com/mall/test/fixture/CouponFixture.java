package com.mall.test.fixture;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 优惠券夹具。种子券多已过期，这里直插一张「全场通用(useType=0)、未来有效、min_point=0」的券，
 * 并给会员发一条 use_status=0 的领取记录，使其在 listCart(enable) 中可用。
 * 用完 delete 清理。
 */
public final class CouponFixture {

    private CouponFixture() {}

    public record TestCoupon(long couponId, long historyId, BigDecimal amount) {}

    public record ManagedCoupon(long couponId, String name, BigDecimal amount) {}

    /** 后台 API 创建全场可领取券所需的最小 body。Date 用 epoch millis，避免本地时区/格式解析差异。 */
    public static Map<String, Object> universalCouponParam(
            String name, BigDecimal amount, int publishCount, int perLimit, LocalDateTime enableTime) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", 0);
        body.put("name", name);
        body.put("platform", 0);
        body.put("amount", amount);
        body.put("perLimit", perLimit);
        body.put("minPoint", new BigDecimal("0.00"));
        body.put("startTime", millis(LocalDateTime.now().minusDays(1)));
        body.put("endTime", millis(LocalDateTime.now().plusYears(1)));
        body.put("useType", 0);
        body.put("note", "admin api auto coupon");
        body.put("publishCount", publishCount);
        body.put("enableTime", millis(enableTime));
        body.put("memberLevel", 0);
        body.put("productRelationList", java.util.List.of());
        body.put("productCategoryRelationList", java.util.List.of());
        return body;
    }

    public static long couponIdByName(String name) {
        return Db.queryLong("SELECT id FROM sms_coupon WHERE name = ? ORDER BY id DESC LIMIT 1", name);
    }

    public static int count(long couponId) {
        return (int) Db.queryLong("SELECT count FROM sms_coupon WHERE id = ?", couponId);
    }

    public static int receiveCount(long couponId) {
        return (int) Db.queryLong("SELECT receive_count FROM sms_coupon WHERE id = ?", couponId);
    }

    public static int historyCount(long couponId) {
        return (int) Db.queryLong("SELECT COUNT(*) FROM sms_coupon_history WHERE coupon_id = ?", couponId);
    }

    public static void deleteManagedCoupon(long couponId) {
        Db.update("DELETE FROM sms_coupon_history WHERE coupon_id=?", couponId);
        Db.update("DELETE FROM sms_coupon_product_relation WHERE coupon_id=?", couponId);
        Db.update("DELETE FROM sms_coupon_product_category_relation WHERE coupon_id=?", couponId);
        Db.update("DELETE FROM sms_coupon WHERE id=?", couponId);
    }

    /** 创建一张面额 amount、门槛 0 的全场通用券并发给会员（未使用）。 */
    public static TestCoupon createUsableUniversalCoupon(long memberId, BigDecimal amount) {
        return createUniversalCoupon(memberId, amount, new BigDecimal("0.00"));
    }

    /** 创建一张面额 amount、使用门槛 minPoint 的全场通用券并发给会员（未使用）。 */
    public static TestCoupon createUniversalCoupon(long memberId, BigDecimal amount, BigDecimal minPoint) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp future = Timestamp.valueOf(LocalDateTime.now().plusYears(5));
        long couponId = Db.insertReturnId(
                "INSERT INTO sms_coupon(type,name,platform,count,amount,per_limit,min_point,start_time,end_time," +
                "use_type,note,publish_count,use_count,receive_count,enable_time,member_level) " +
                "VALUES(0,'TEST-AUTO-COUPON',0,999,?,99,?,?,?,0,'auto test',999,0,0,?,0)",
                amount, minPoint, now, future, now);
        long historyId = Db.insertReturnId(
                "INSERT INTO sms_coupon_history(coupon_id,member_id,coupon_code,member_nickname,get_type,create_time,use_status) " +
                "VALUES(?,?,?,'test',1,?,0)",
                couponId, memberId, "TC" + (System.currentTimeMillis() % 100000000L), now);
        return new TestCoupon(couponId, historyId, amount);
    }

    /** 创建一张可领取的全场券（per_limit=1, enable_time 已到），**不**预置领取记录，供 /member/coupon/add 领取。 */
    public static TestCoupon createUniversalCouponForClaim(BigDecimal amount) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp future = Timestamp.valueOf(LocalDateTime.now().plusYears(5));
        Timestamp past = Timestamp.valueOf(LocalDateTime.now().minusDays(1));
        long couponId = Db.insertReturnId(
                "INSERT INTO sms_coupon(type,name,platform,count,amount,per_limit,min_point,start_time,end_time," +
                "use_type,note,publish_count,use_count,receive_count,enable_time,member_level) " +
                "VALUES(0,'TEST-CLAIM-COUPON',0,999,?,1,0.00,?,?,0,'auto claim test',999,0,0,?,0)",
                amount, now, future, past);
        return new TestCoupon(couponId, 0, amount);
    }

    /** 该券领取记录的使用状态：0未用/1已用。 */
    public static int historyUseStatus(long couponId, long memberId) {
        return (int) Db.queryLong(
                "SELECT use_status FROM sms_coupon_history WHERE coupon_id=? AND member_id=? ORDER BY id DESC LIMIT 1",
                couponId, memberId);
    }

    public static void delete(TestCoupon c) {
        Db.update("DELETE FROM sms_coupon_history WHERE coupon_id=?", c.couponId());
        Db.update("DELETE FROM sms_coupon WHERE id=?", c.couponId());
    }

    private static long millis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
