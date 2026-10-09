package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SUBORDINATE_SALES_NOT_MANAGED;

@Component
public class SubordinateSalesObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private LeadObjectPermissionService permissionService;

    @Override
    public String getBizType() { return "subordinate-sales"; }

    @Override
    public boolean hasPermission(Long salesUserId, String action, Long managerUserId) {
        return salesUserId != null && managerUserId != null && "newcomer-pool".equals(action)
                && permissionService.getManagedUserIds(managerUserId).contains(salesUserId);
    }

    @Override
    public void check(Long salesUserId, String action, Long managerUserId) {
        if (!hasPermission(salesUserId, action, managerUserId)) {
            throw exception(SUBORDINATE_SALES_NOT_MANAGED);
        }
    }
}
