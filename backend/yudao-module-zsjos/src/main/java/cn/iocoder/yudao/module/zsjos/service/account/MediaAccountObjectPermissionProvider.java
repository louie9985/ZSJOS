package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        return hasPermission(account, action, userId, permissionApi.hasTenantReadAllAccess(userId),
                permissionApi.hasAnyPermissions(userId, "zsjos:media-account:query-all"), relationMapper::selectById);
    }

    /** The inbox batches reads but must retain precisely the single-account authorization rules. */
    public List<MediaAccountDO> filterReadable(List<MediaAccountDO> accounts, Long userId) {
        if (accounts.isEmpty()) return List.of();
        var relationIds = accounts.stream().map(MediaAccountDO::getCreateServiceRelationId)
                .filter(Objects::nonNull).distinct().toList();
        var relations = relationIds.isEmpty()
                ? Map.<Long, ServiceRelationDO>of()
                : relationMapper.selectByIds(relationIds).stream().collect(Collectors.toMap(
                        ServiceRelationDO::getId, row -> row));
        boolean readAll = permissionApi.hasTenantReadAllAccess(userId);
        boolean queryAll = permissionApi.hasAnyPermissions(userId, "zsjos:media-account:query-all");
        return accounts.stream().filter(account -> hasPermission(account, "read", userId,
                readAll, queryAll, relations::get)).toList();
    }

    private boolean hasPermission(MediaAccountDO account, String action, Long userId, boolean readAll,
            boolean queryAll, Function<Long,
            ServiceRelationDO> relationLookup) {
        if (("delete_pending".equals(account.getRunStatus()) || "deleted".equals(account.getRunStatus())) && !"read".equals(action)) return false;
        if ("read".equals(action) && readAll) {
            if (account.getCreateServiceRelationId() == null) return true;
            var source = relationLookup.apply(account.getCreateServiceRelationId());
            // Historical state is readable, but a broken or cross-person source is not a valid association.
            return source != null && Objects.equals(source.getPersonId(), account.getStudentPersonId())
                    && Objects.equals(source.getTenantId(), account.getTenantId());
        }
        if ("read".equals(action) && queryAll) return true;
        if ("maintenance".equals(action)
                && queryAll) return true;
        boolean related;
        boolean operator;
        if (account.getCreateServiceRelationId() != null) {
            var relation = relationLookup.apply(account.getCreateServiceRelationId());
            if (relation == null || !Objects.equals(relation.getPersonId(), account.getStudentPersonId())
                    || !Objects.equals(relation.getTenantId(), account.getTenantId())
                    || !"active".equals(relation.getStatus()) || !"accepted".equals(relation.getAcceptanceStatus())) return false;
            operator = userId.equals(relation.getOperatorUserId());
            related = operator || userId.equals(relation.getContentDirectorUserId());
        } else {
            // Legacy accounts have no proven group; only their explicit stored owners retain access.
            operator = userId.equals(account.getOwnerOperatorUserId());
            related = operator || userId.equals(account.getDirectorUserId());
        }
        if ("production-ticket-create".equals(action)) {
            return operator;
        }
        return related && ("read".equals(action) || "update".equals(action) || "edit".equals(action)
                || "maintenance".equals(action) || "positioning-apply".equals(action)
                || "grade".equals(action) || "rescue".equals(action)
                || "bind-student".equals(action) || "rebind".equals(action) || "delete".equals(action));
    }

    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
    }
}
