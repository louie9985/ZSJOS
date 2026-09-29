package cn.iocoder.yudao.module.zsjos.service.cashback;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
@Component
public class CashbackObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private CashbackMapper mapper;
    @Resource private PermissionApi permissions;
    @Override public String getBizType() { return "cashback"; }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        try { check(id, action, userId); return true; } catch (ServiceException ex) { return false; }
    }
    @Override public void check(Long id, String action, Long userId) {
        if (mapper.selectById(id) == null) throw exception(CASHBACK_NOT_EXISTS);
        boolean query = permissions.hasAnyPermissions(userId, "zsjos:cashback:finance-query");
        boolean allowed = switch (action) {
            case "read" -> query;
            case "block" -> query && permissions.hasAnyPermissions(userId, "zsjos:cashback:block");
            case "unblock" -> query && permissions.hasAnyPermissions(userId, "zsjos:cashback:unblock");
            default -> false;
        };
        if (!allowed) throw exception(CASHBACK_CONTROL_DENIED);
    }
}
