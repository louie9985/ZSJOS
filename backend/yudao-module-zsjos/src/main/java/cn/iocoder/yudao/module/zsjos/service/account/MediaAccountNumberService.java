package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountNoDailyCounterMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class MediaAccountNumberService {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 当日序号上限，超过后回到 1 循环使用。 */
    private static final long MAX_DAILY_SEQUENCE = 9999L;
    @Resource private MediaAccountNoDailyCounterMapper counterMapper;

    @Transactional(rollbackFor = Exception.class)
    public String next() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDate sequenceDate = now.toLocalDate();
        counterMapper.insertIfAbsent(tenantId, sequenceDate);
        counterMapper.increment(tenantId, sequenceDate, MAX_DAILY_SEQUENCE);
        long value = counterMapper.selectReservedValue(tenantId, sequenceDate);
        if (value < 1 || value > MAX_DAILY_SEQUENCE) {
            throw new IllegalStateException("Failed to reserve media account number");
        }
        return "MA" + now.format(TIMESTAMP) + String.format(Locale.ROOT, "%04d", value);
    }
}
