package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadNoDailyCounterMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class LeadNumberService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource private LeadNoDailyCounterMapper counterMapper;

    @Transactional(rollbackFor = Exception.class)
    public String next(LocalDateTime submittedAt) {
        counterMapper.reserve(TenantContextHolder.getRequiredTenantId(), submittedAt.toLocalDate());
        long sequence = counterMapper.selectReservedValue();
        if (sequence <= 0) {
            throw new IllegalStateException("Failed to reserve Lead business number");
        }
        return "KZ" + submittedAt.format(TIMESTAMP_FORMAT) + String.format(Locale.ROOT, "%04d", sequence);
    }
}
