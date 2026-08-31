package cn.iocoder.yudao.module.zsjos.service.production;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.BIZ_TYPE_PRODUCTION_TICKET;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCTION_TICKET_PERMISSION_DENIED;

@Component
public class ProductionTicketObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private ProductionTicketMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Override public String getBizType() { return BIZ_TYPE_PRODUCTION_TICKET; }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        ProductionTicketDO ticket = mapper.selectById(id);
        if (ticket == null) return false;
        return switch (action) {
            case "read" -> permissionApi.hasAnyPermissions(userId, "zsjos:production-ticket:query-all")
                    || Objects.equals(userId, ticket.getOwnerOperatorUserId())
                    || Objects.equals(userId, ticket.getAssigneeFilmingEditorUserId())
                    || Objects.equals(userId, ticket.getReviewerUserId())
                    || "public_pool".equals(ticket.getStatus()) && permissionApi.hasAnyPermissions(userId,
                    "zsjos:production-ticket:pool-query", "zsjos:production-ticket:claim");
            case "accept", "produce", "submit" -> Objects.equals(userId, ticket.getAssigneeFilmingEditorUserId());
            case "reject-assignment" -> "pending_accept".equals(ticket.getStatus())
                    && Objects.equals(userId, ticket.getAssigneeFilmingEditorUserId());
            case "claim" -> "public_pool".equals(ticket.getStatus())
                    && ticket.getAssigneeFilmingEditorUserId() == null
                    && permissionApi.hasAnyPermissions(userId, "zsjos:production-ticket:claim");
            case "check" -> Objects.equals(userId, ticket.getReviewerUserId());
            default -> false;
        };
    }
    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(PRODUCTION_TICKET_PERMISSION_DENIED);
    }
}
