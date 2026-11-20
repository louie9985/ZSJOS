package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_EFFECTIVE;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;

@Component
public class TextbookShippingPermission implements ZsjosObjectPermissionProvider {
    @Resource private PermissionApi permissions;
    @Resource private SalesOrderMapper orders;

    public String getBizType() { return "textbook-shipping"; }
    public boolean canViewAll(Long actor) {
        return actor != null && permissions.hasAnyPermissions(actor, "zsjos:textbook-shipping:view-all");
    }
    boolean visible(SalesOrderDO order, Long actor) {
        return actor != null && order != null && STATUS_EFFECTIVE.equals(order.getStatus())
                && (actor.equals(order.getFormalSalesUserId()) || canViewAll(actor));
    }
    public boolean hasPermission(Long orderId, String action, Long actor) {
        return actor != null && "update".equals(action) && permissions.hasAnyPermissions(actor, "zsjos:textbook-shipping:query")
                && permissions.hasAnyPermissions(actor, "zsjos:textbook-shipping:update") && visible(orders.selectById(orderId), actor);
    }
    public void check(Long id, String action, Long actor) {
        if (!hasPermission(id, action, actor)) throw exception(LEAD_PERMISSION_DENIED);
    }
}
