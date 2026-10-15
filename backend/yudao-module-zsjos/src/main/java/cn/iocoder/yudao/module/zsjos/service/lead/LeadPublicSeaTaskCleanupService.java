package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaTaskCleanupMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class LeadPublicSeaTaskCleanupService {
    @Resource private LeadPublicSeaTaskCleanupMapper mapper;

    @Transactional(rollbackFor=Exception.class)
    public int cancelObsolete(Long leadId, LocalDateTime now) {
        Long tenantId=TenantContextHolder.getRequiredTenantId();
        var ids=mapper.selectCandidates(tenantId,leadId);
        if(ids.isEmpty()) return 0;
        return mapper.cancelObsolete(tenantId,ids,now,"公海跟进责任变更，取消原跟进任务");
    }
}
