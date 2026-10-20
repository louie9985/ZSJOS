package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.hutool.crypto.digest.DigestUtil;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import org.redisson.api.RateType;
import org.springframework.stereotype.Component;
import java.time.Duration;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.TOO_MANY_REQUESTS;

@Component
public class StudentInfoUploadRateLimit {
    @Resource private RedissonClient redis;
    public void check(String token,String ip) {
        acquire("token:"+StudentInfoFormService.hash(token),30); acquire("ip:"+DigestUtil.sha256Hex(ip),120);
    }
    private void acquire(String key,int count) {
        var limiter=redis.getRateLimiter("zsjos:student-info:upload:"+key);
        limiter.trySetRate(RateType.OVERALL,count,Duration.ofMinutes(1)); limiter.expire(Duration.ofMinutes(2));
        if (!limiter.tryAcquire()) throw exception(TOO_MANY_REQUESTS);
    }
}
