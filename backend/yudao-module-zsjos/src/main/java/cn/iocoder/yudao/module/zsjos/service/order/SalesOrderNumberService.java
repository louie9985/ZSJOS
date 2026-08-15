package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderNoDailyCounterMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class SalesOrderNumberService {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    @Resource private SalesOrderNoDailyCounterMapper counterMapper;

    @Transactional(rollbackFor = Exception.class)
    public String next() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        counterMapper.reserve(TenantContextHolder.getRequiredTenantId(), now.toLocalDate());
        long value = counterMapper.selectReservedValue();
        if (value < 1 || value > 9999) throw new IllegalStateException("Failed to reserve sales order number");
        return "OD" + now.format(TIMESTAMP) + String.format(Locale.ROOT, "%04d", value);
    }
}
