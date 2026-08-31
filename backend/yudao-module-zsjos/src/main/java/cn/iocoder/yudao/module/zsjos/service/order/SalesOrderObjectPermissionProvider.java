package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class SalesOrderObjectPermissionProvider implements ZsjosObjectPermissionProvider {

    @Resource
    private SalesOrderObjectPermissionService permissionService;

    @Override
    public String getBizType() {
        return "sales-order";
    }

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        try {
            permissionService.check(bizId, action);
            return true;
        } catch (ServiceException ex) {
            return false;
        }
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        permissionService.check(bizId, action);
    }
}
