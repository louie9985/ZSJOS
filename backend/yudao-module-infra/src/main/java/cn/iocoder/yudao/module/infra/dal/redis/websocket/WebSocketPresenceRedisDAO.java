package cn.iocoder.yudao.module.infra.dal.redis.websocket;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Repository
public class WebSocketPresenceRedisDAO {

    static final long PRESENCE_TIMEOUT_MILLIS = 90_000L;
    private static final Duration KEY_TTL = Duration.ofDays(1);
    private static final String KEY_PREFIX = "infra:websocket:presence:";
    private static final String MEMBER_SEPARATOR = ":";

    private final StringRedisTemplate redisTemplate;

    public WebSocketPresenceRedisDAO(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void touch(Long tenantId, Integer userType, Long userId, String sessionId, long nowMillis) {
        String key = buildKey(tenantId, userType);
        redisTemplate.opsForZSet().add(key, buildMember(userId, sessionId), nowMillis);
        redisTemplate.expire(key, KEY_TTL);
    }

    public void remove(Long tenantId, Integer userType, Long userId, String sessionId) {
        redisTemplate.opsForZSet().remove(buildKey(tenantId, userType), buildMember(userId, sessionId));
    }

    public Set<Long> getOnlineUserIds(Long tenantId, Integer userType, long nowMillis) {
        String key = buildKey(tenantId, userType);
        double expiredScore = nowMillis - PRESENCE_TIMEOUT_MILLIS;
        redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, expiredScore);
        Set<String> members = redisTemplate.opsForZSet().rangeByScore(key, expiredScore + 1, Double.POSITIVE_INFINITY);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (String member : members) {
            int separatorIndex = member.indexOf(MEMBER_SEPARATOR);
            if (separatorIndex <= 0) {
                continue;
            }
            try {
                userIds.add(Long.valueOf(member.substring(0, separatorIndex)));
            } catch (NumberFormatException ignored) {
                // 忽略无法识别的旧数据，后续会随超时清理。
            }
        }
        return userIds;
    }

    private static String buildKey(Long tenantId, Integer userType) {
        return KEY_PREFIX + tenantId + ":" + userType;
    }

    private static String buildMember(Long userId, String sessionId) {
        return userId + MEMBER_SEPARATOR + sessionId;
    }

}
