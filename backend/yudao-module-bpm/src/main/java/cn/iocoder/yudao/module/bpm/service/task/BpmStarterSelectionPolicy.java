package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmStarterSelectionScopeRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** For START_USER_SELECT only, candidateParam is the configured role-ID union. */
@Service
public class BpmStarterSelectionPolicy {
    public static final ErrorCode INVALID_SCOPE = new ErrorCode(1_009_003_090, "发起人自选角色范围无效，请管理员检查流程配置");
    public static final ErrorCode OUT_OF_SCOPE = new ErrorCode(1_009_003_091, "所选审批人已不在流程允许的角色范围内，请重新选择");
    @Resource private RoleApi roleApi;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;

    public List<Long> parseRoleIds(String param) {
        if (param == null || param.isBlank()) return List.of();
        if (!param.matches("[1-9][0-9]*(,[1-9][0-9]*)*")) throw exception(INVALID_SCOPE);
        try { return Arrays.stream(param.split(",")).map(Long::valueOf).distinct().toList(); }
        catch (NumberFormatException e) { throw exception(INVALID_SCOPE); }
    }

    public BpmStarterSelectionScopeRespDTO resolve(String param) {
        var ids = parseRoleIds(param);
        if (ids.isEmpty()) return null;
        var roles = roleApi.getRoleList(ids);
        var enabled = roles.stream().filter(r -> CommonStatusEnum.isEnable(r.getStatus()))
                .map(RoleRespDTO::getId).filter(ids::contains).distinct().toList();
        if (enabled.size() != ids.size()) throw exception(INVALID_SCOPE);
        var members = permissionApi.getUserRoleIdListByRoleIds(enabled);
        var scope = new BpmStarterSelectionScopeRespDTO();
        scope.setRoleIds(ids);
        scope.setUserIds(members.isEmpty() ? List.of() : adminUserApi.getUserList(members).stream()
                .filter(u -> members.contains(u.getId()) && CommonStatusEnum.isEnable(u.getStatus()))
                .map(u -> u.getId()).filter(Objects::nonNull).distinct().sorted().toList());
        return scope;
    }

    public void validate(String param, Collection<Long> selected) {
        var scope = resolve(param);
        if (scope != null && (selected == null || selected.isEmpty() || !scope.getUserIds().containsAll(selected)))
            throw exception(OUT_OF_SCOPE);
    }
}
