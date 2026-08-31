package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.BIZ_TYPE_MEDIA_ACCOUNT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_PERMISSION_DENIED;

@Component
public class MediaAccountObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private MediaAccountMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Resource private ServiceRelationMapper relationMapper;

    @Override public String getBizType() { return BIZ_TYPE_MEDIA_ACCOUNT; }

    @Override
    public boolean hasPermission(Long id, String action, Long userId) {
        MediaAccountDO account = mapper.selectById(id);
        if (account == null) return false;
        if ("read".equals(action) && permissionApi.hasAnyPermissions(userId, "zsjos:media-account:query-all")) return true;
        if ("maintenance".equals(action)
                && permissionApi.hasAnyPermissions(userId, "zsjos:media-account:query-all")) return true;
        if ("read".equals(action) && account.getStudentPersonId() != null
                && relationMapper.existsActiveByDirectorOrOperatorAndPerson(userId, account.getStudentPersonId())) return true;
        boolean related = userId.equals(account.getOwnerOperatorUserId()) || userId.equals(account.getDirectorUserId());
        if ("production-ticket-create".equals(action)) {
            return userId.equals(account.getOwnerOperatorUserId());
        }
        return related && ("read".equals(action) || "update".equals(action) || "edit".equals(action)
                || "maintenance".equals(action)
                || "grade".equals(action) || "rescue".equals(action)
                || "bind-student".equals(action) || "rebind".equals(action));
    }

    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
    }
}
