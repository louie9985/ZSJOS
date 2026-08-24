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

    @Resource private PersonNoDailyCounterMapper counterMapper;

    @Transactional(rollbackFor = Exception.class)
    public String next() {
        return next(LocalDateTime.now(BEIJING));
    }

    String next(LocalDateTime now) {
        counterMapper.reserve(TenantContextHolder.getRequiredTenantId(), now.toLocalDate());
        long value = counterMapper.selectReservedValue();
        if (value < 1 || value > 9999) {
            throw new IllegalStateException("Failed to reserve Person business number");
        }
        return "XY" + now.format(TIMESTAMP) + String.format(Locale.ROOT, "%04d", value);
    }
}
