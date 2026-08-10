package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class LeadDispatchRedisRepository {

    private static final long PRESENCE_TTL_SECONDS = 90L;
    private static final DefaultRedisScript<Long> HEARTBEAT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 0 then
              redis.call('LREM', KEYS[1], 0, ARGV[1])
              redis.call('RPUSH', KEYS[1], ARGV[1])
            end
            redis.call('SET', KEYS[2], 'online', 'EX', ARGV[3])
            redis.call('SET', KEYS[3], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> OFFLINE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('LREM', KEYS[1], 0, ARGV[1])
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local removed = 0
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              removed = removed + redis.call('DEL', KEYS[1])
            end
            if redis.call('GET', KEYS[2]) == ARGV[2] then
              removed = removed + redis.call('DEL', KEYS[2])
            end
            return removed
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public LeadDispatchRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void heartbeat(Long saleId, boolean accepting) {
        redisTemplate.execute(HEARTBEAT_SCRIPT,
                List.of(poolKey(), presenceKey(saleId), modeKey(saleId)),
                saleId.toString(), accepting ? "accepting" : "paused", String.valueOf(PRESENCE_TTL_SECONDS));
    }

    public void offline(Long saleId) {
        redisTemplate.execute(OFFLINE_SCRIPT, List.of(poolKey(), presenceKey(saleId)), saleId.toString());
    }

    public void cacheMode(Long saleId, boolean accepting) {
        redisTemplate.opsForValue().set(modeKey(saleId), accepting ? "accepting" : "paused");
    }

    public boolean isOnline(Long saleId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(presenceKey(saleId)));
    }

    public boolean isAccepting(Long saleId) {
        return "accepting".equals(redisTemplate.opsForValue().get(modeKey(saleId)));
    }

    public long poolSize() {
        Long size = redisTemplate.opsForList().size(poolKey());
        return size == null ? 0L : size;
    }

    public Long rotateNext() {
        String value = redisTemplate.opsForList().rightPopAndLeftPush(poolKey(), poolKey());
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            redisTemplate.opsForList().remove(poolKey(), 0, value);
            return null;
        }
    }

    public void removeFromPool(Long saleId) {
        redisTemplate.opsForList().remove(poolKey(), 0, saleId.toString());
    }

    public boolean tryReserve(Long leadId, Long saleId, int timeoutSeconds) {
        Long result = redisTemplate.execute(RESERVE_SCRIPT,
                List.of(leadLockKey(leadId), pendingKey(saleId)),
                saleId.toString(), leadId.toString(), String.valueOf(timeoutSeconds));
        return Long.valueOf(1L).equals(result);
    }

    public void release(Long leadId, Long saleId) {
        if (leadId == null || saleId == null) {
            return;
        }
        redisTemplate.execute(RELEASE_SCRIPT, List.of(leadLockKey(leadId), pendingKey(saleId)),
                saleId.toString(), leadId.toString());
    }

    private String prefix() {
        return "zsjos:lead-dispatch:" + TenantContextHolder.getRequiredTenantId() + ":";
    }

    private String poolKey() { return prefix() + "sale:pool"; }
    private String presenceKey(Long saleId) { return prefix() + "sale:presence:" + saleId; }
    private String modeKey(Long saleId) { return prefix() + "sale:mode:" + saleId; }
    private String pendingKey(Long saleId) { return prefix() + "sale:pending:" + saleId; }
    private String leadLockKey(Long leadId) { return prefix() + "lead:lock:" + leadId; }
}
