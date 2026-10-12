package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import org.redisson.api.RedissonClient;
import org.redisson.api.RateType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.time.Duration;
@Component public class PointsRequestRateLimit {
    @Resource private RedissonClient redis;
    public void check(String action, Long userId, int count) {
        var limiter = redis.getRateLimiter("zsjos:points:rate:" + action + ":" + userId);
        limiter.trySetRate(RateType.OVERALL, count, Duration.ofMinutes(1));
        limiter.expire(Duration.ofMinutes(2));
        if (!limiter.tryAcquire()) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.TOO_MANY_REQUESTS);
    }
}
