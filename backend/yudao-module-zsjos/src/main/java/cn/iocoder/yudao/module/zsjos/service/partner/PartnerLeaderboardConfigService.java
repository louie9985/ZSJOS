package cn.iocoder.yudao.module.zsjos.service.partner;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.partner.PartnerLeaderboardConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.partner.PartnerLeaderboardConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class PartnerLeaderboardConfigService {
    public static final List<String> TYPES = List.of("estimated_income", "withdrawn_amount", "lead_count", "valid_lead_count");
    public static final List<String> PERIODS = List.of("today", "week", "month", "total");
    @Resource private PartnerLeaderboardConfigMapper mapper;
    @Resource private RoleApi roleApi;
    @Resource private PermissionApi permissionApi;

    public PartnerLeaderboardConfigDO get() {
        PartnerLeaderboardConfigDO row = mapper.selectCurrent();
        if (row != null) return row;
        return new PartnerLeaderboardConfigDO().setEnabled(true).setIncludeEmployeeSubmitter(false)
                .setEmployeeRoleCodes("").setEnabledTypes(String.join(",", TYPES))
                .setDefaultType(TYPES.get(0)).setDefaultPeriod("month").setPageSize(20).setMaskName(true);
    }

    public static List<String> splitCodes(String codes) {
        if (codes == null || codes.isBlank() || "[]".equals(codes)) return List.of();
        return Arrays.stream(codes.split(",")).map(String::trim).filter(code -> !code.isEmpty()).distinct().toList();
    }

    public Set<Long> eligibleEmployeeIds(PartnerLeaderboardConfigDO config) {
        if (!Boolean.TRUE.equals(config.getIncludeEmployeeSubmitter())) return Set.of();
        List<Long> roleIds = splitCodes(config.getEmployeeRoleCodes()).stream()
                .map(roleApi::getRoleByCode).filter(Objects::nonNull)
                .filter(role -> Integer.valueOf(0).equals(role.getStatus()))
                .map(RoleRespDTO::getId).toList();
        return roleIds.isEmpty() ? Set.of() : permissionApi.getUserRoleIdListByRoleIds(roleIds);
    }

    public void save(PartnerLeaderboardConfigDO value) {
        List<String> enabled = splitCodes(value.getEnabledTypes());
        if (enabled.isEmpty() || enabled.stream().anyMatch(type -> !TYPES.contains(type))) throw new IllegalArgumentException("榜单配置无效");
        if (!enabled.contains(value.getDefaultType()) || !PERIODS.contains(value.getDefaultPeriod())) throw new IllegalArgumentException("默认榜单或周期无效");
        value.setEnabledTypes(String.join(",", enabled));
        PartnerLeaderboardConfigDO current = mapper.selectCurrent();
        if (current == null) mapper.insert(value); else { value.setId(current.getId()); mapper.updateById(value); }
    }
}
