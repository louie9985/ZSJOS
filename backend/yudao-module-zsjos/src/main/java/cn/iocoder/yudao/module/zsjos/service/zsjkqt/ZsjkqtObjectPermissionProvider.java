package cn.iocoder.yudao.module.zsjos.service.zsjkqt;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.zsjkqt.ZsjkqtDraftMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
@Component
public class ZsjkqtObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private ZsjkqtDraftMapper drafts;
    @Resource private PermissionApi permissions;
    public String getBizType() {
        return "zsjkqt-draft";
    }
    public boolean hasPermission(Long id, String action, Long userId) {
        // Explicit application contract: all authorized users share all tenant-local drafts.
        return userId!=null && Set.of("read", "update", "delete", "export").contains(action)
        && permissions.hasAnyPermissions(userId, ZsjkqtErrors.ACCESS) && drafts.selectById(id)!=null;
    }
    public void check(Long id, String action, Long userId) {
        if(userId==null || !permissions.hasAnyPermissions(userId, ZsjkqtErrors.ACCESS)) throw exception(ZsjkqtErrors.DENIED);
        if(drafts.selectById(id)==null) throw exception(ZsjkqtErrors.NOT_FOUND);
        if(!Set.of("read", "update", "delete", "export").contains(action)) throw exception(ZsjkqtErrors.DENIED);
    }
}
