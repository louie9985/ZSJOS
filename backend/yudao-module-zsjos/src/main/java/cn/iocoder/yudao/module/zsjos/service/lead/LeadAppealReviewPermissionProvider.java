package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_APPEAL_PERMISSION_DENIED;

@Component
public class LeadAppealReviewPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private LeadAppealService appealService;
    public String getBizType() { return "lead-appeal-review"; }
    public boolean hasPermission(Long bizId, String action, Long userId) {
        // Exact live/completed task correlation is additionally enforced by getReviewContext.
        return "read".equals(action) && appealService.canReviewAppeal(bizId, userId);
    }
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(LEAD_APPEAL_PERMISSION_DENIED);
    }
}
