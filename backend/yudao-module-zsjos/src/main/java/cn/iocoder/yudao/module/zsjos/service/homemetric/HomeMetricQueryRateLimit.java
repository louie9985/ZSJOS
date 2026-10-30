package cn.iocoder.yudao.module.zsjos.service.homemetric;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.redisson.api.*;
import org.springframework.stereotype.Component;
import java.time.Duration;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.TOO_MANY_REQUESTS;
/** Bounds repeated aggregation requests across instances; the frontend separately queues four calls. */
@Component public class HomeMetricQueryRateLimit {
 @Resource private RedissonClient redisson;
 public void check(Long actor){var limiter=redisson.getRateLimiter("zsjos:home-metric:rate:"+TenantContextHolder.getRequiredTenantId()+":"+actor);limiter.trySetRate(RateType.OVERALL,120,Duration.ofMinutes(1));limiter.expire(Duration.ofMinutes(2));if(!limiter.tryAcquire())throw exception(TOO_MANY_REQUESTS);}
}
