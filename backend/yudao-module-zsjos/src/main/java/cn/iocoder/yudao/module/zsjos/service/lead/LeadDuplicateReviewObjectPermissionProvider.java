package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadDuplicateReviewMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.BIZ_TYPE_LEAD_DUPLICATE_REVIEW;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_DUPLICATE_REVIEW_PERMISSION_DENIED;

@Component
public class LeadDuplicateReviewObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private LeadDuplicateReviewMapper mapper;
    @Resource private SecurityFrameworkService securityFrameworkService;

    @Override public String getBizType() { return BIZ_TYPE_LEAD_DUPLICATE_REVIEW; }

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        if (mapper.selectById(bizId) == null) return false;
        return securityFrameworkService.hasPermission("process".equals(action)
                ? "zsjos:lead-duplicate-review:process" : "zsjos:lead-duplicate-review:query");
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(LEAD_DUPLICATE_REVIEW_PERMISSION_DENIED);
    }
}
