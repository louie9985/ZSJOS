package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_CONTACT_CONFLICT;

/** Resolve historical links before calling the existing object-authorized service boundary. */
@Service
public class PersonMergeIdentityService {
    @Resource private PersonMapper personMapper;
    public Long resolve(Long id) {
        if (id == null) return null;
        var seen = new HashSet<Long>();
        Long current=id;
        for (int depth=0;depth<32;depth++) {
            if (!seen.add(current)) throw exception(LEAD_CONTACT_CONFLICT);
            Long next=personMapper.selectMergedTarget(current,TenantContextHolder.getRequiredTenantId());
            if (next==null) return current;
            current=next;
        }
        throw exception(LEAD_CONTACT_CONFLICT);
    }
}
