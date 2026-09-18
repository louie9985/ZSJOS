package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadNoDailyCounterMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class LeadNumberService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 当日序号上限，超过后回到 1 循环使用。 */
    private static final long MAX_DAILY_SEQUENCE = 9999L;

    @Resource private LeadNoDailyCounterMapper counterMapper;

    @Transactional(rollbackFor = Exception.class)
    public String next(LocalDateTime submittedAt) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDate sequenceDate = submittedAt.toLocalDate();
        counterMapper.insertIfAbsent(tenantId, sequenceDate);
        counterMapper.increment(tenantId, sequenceDate, MAX_DAILY_SEQUENCE);
        long sequence = counterMapper.selectReservedValue(tenantId, sequenceDate);
        if (sequence < 1 || sequence > MAX_DAILY_SEQUENCE) {
            throw new IllegalStateException("Failed to reserve Lead business number");
        }
        return "KZ" + submittedAt.format(TIMESTAMP_FORMAT) + String.format(Locale.ROOT, "%04d", sequence);
    }
}
