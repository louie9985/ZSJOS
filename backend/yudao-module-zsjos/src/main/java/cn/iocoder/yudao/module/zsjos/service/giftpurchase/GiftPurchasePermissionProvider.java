package cn.iocoder.yudao.module.zsjos.service.giftpurchase;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.GiftPurchaseMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.GiftPurchaseErrorCodes.*;
@Component
public class GiftPurchasePermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private GiftPurchaseMapper mapper;
    @Resource private PermissionApi permissions;
    public String getBizType() { return "gift-purchase"; }
    public boolean hasPermission(Long id, String action, Long userId) {
        if (id == null || userId == null || !"batch-purchase".equals(action)) return false;
        var row = mapper.selectById(id);
        // Procurement is tenant-wide Admin management; source salesperson is not its owner.
        return row != null && TenantContextHolder.getTenantId() != null
                && Objects.equals(row.getTenantId(), TenantContextHolder.getTenantId())
                && permissions.hasAnyPermissions(userId, "zsjos:gift-purchase:batch-purchase");
    }
    public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(DENIED);
    }
}
