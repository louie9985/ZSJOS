package cn.iocoder.yudao.module.zsjos.service.content;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.BIZ_TYPE_CONTENT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_PERMISSION_DENIED;

@Component
public class ContentObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private ContentMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Resource private MediaAccountMapper accountMapper;
    @Override public String getBizType() { return BIZ_TYPE_CONTENT; }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        ContentDO content = mapper.selectById(id);
        if (content == null) return false;
        if (permissionApi.hasAnyPermissions(userId, "zsjos:content:query-all")) return true;
        if (userId.equals(content.getOwnerOperatorUserId()) || userId.equals(content.getFilmingEditorUserId())) return true;
        MediaAccountDO account = accountMapper.selectById(content.getAccountId());
        return account != null && (userId.equals(account.getDirectorUserId())
                || userId.equals(account.getOwnerOperatorUserId()));
    }
    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(CONTENT_PERMISSION_DENIED);
    }
}
