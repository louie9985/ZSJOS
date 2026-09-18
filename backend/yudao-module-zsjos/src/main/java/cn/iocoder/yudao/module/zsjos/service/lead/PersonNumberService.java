package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonNoDailyCounterMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class PersonNumberService {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 当日序号上限，超过后回到 1 循环使用。 */
    private static final long MAX_DAILY_SEQUENCE = 9999L;

    @Resource private PersonNoDailyCounterMapper counterMapper;

    @Transactional(rollbackFor = Exception.class)
    public String next() {
        return next(LocalDateTime.now(BEIJING));
    }

    String next(LocalDateTime now) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        counterMapper.reserve(tenantId, now.toLocalDate(), MAX_DAILY_SEQUENCE);
        long value = counterMapper.selectReservedValue(tenantId, now.toLocalDate());
        if (value < 1 || value > MAX_DAILY_SEQUENCE) {
            throw new IllegalStateException("Failed to reserve Person business number");
        }
        return "XY" + now.format(TIMESTAMP) + String.format(Locale.ROOT, "%04d", value);
    }
}
