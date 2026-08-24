package cn.iocoder.yudao.module.hrm.service.birthdaycare;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.hrm.api.birthdaycare.HrmBirthdayCareConfig;
import cn.iocoder.yudao.module.hrm.api.birthdaycare.HrmBirthdayCareDispatch;
import cn.iocoder.yudao.module.hrm.api.birthdaycare.HrmBirthdayCareEmployee;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import cn.iocoder.yudao.module.hrm.enums.config.HrmConfigTypeEnum;
import cn.iocoder.yudao.module.hrm.enums.employee.info.HrmEmployeeEntryStatusEnum;
import cn.iocoder.yudao.module.hrm.service.config.HrmConfigService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HrmBirthdayCareServiceImpl implements HrmBirthdayCareService {

    private static final String TASK_PERMISSION = "zsjos:business-task:query";

    @Resource private HrmConfigService hrmConfigService;
    @Resource private HrmEmployeeMapper employeeMapper;
    @Resource private DeptApi deptApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PermissionApi permissionApi;
    @Resource private ObjectMapper objectMapper;

    @Override
    public HrmBirthdayCareConfig getConfig() {
        List<String> values = hrmConfigService.getConfigValueList(HrmConfigTypeEnum.BIRTHDAY_CARE.getType());
        if (CollUtil.isEmpty(values)) return HrmBirthdayCareConfig.defaults();
        try {
            HrmBirthdayCareConfig config = objectMapper.readValue(values.get(0), HrmBirthdayCareConfig.class);
            return normalize(config);
        } catch (Exception ignored) {
            return HrmBirthdayCareConfig.defaults();
        }
    }

    @Override
    @Transactional
    public void saveConfig(HrmBirthdayCareConfig config) {
        try {
            hrmConfigService.replaceConfigValueList(HrmConfigTypeEnum.BIRTHDAY_CARE.getType(),
                    List.of(objectMapper.writeValueAsString(normalize(config))));
        } catch (Exception exception) {
            throw new IllegalStateException("生日关怀配置保存失败", exception);
        }
    }

    @Override
    public HrmBirthdayCareDispatch getDueDispatch(LocalDate today, LocalTime now) {
        HrmBirthdayCareConfig config = getConfig();
        if (!config.enabled() || config.deptIds().isEmpty() || now.isBefore(config.triggerTime())) {
            return new HrmBirthdayCareDispatch(today.plusDays(config.advanceDays()),
                    today.atTime(config.triggerTime()), List.of(), List.of());
        }
        Set<Long> deptIds = expandDeptIds(config);
        List<Integer> activeStatuses = HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES.stream().toList();
        LocalDate birthdayDate = today.plusDays(config.advanceDays());
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);
        List<HrmBirthdayCareEmployee> employees = employeeMapper.selectBirthdayList(activeStatuses, birthdayDate)
                .stream().filter(employee -> deptIds.contains(employee.getDeptId()))
                .map(employee -> new HrmBirthdayCareEmployee(employee.getId(), employee.getName(), employee.getDeptId(),
                        deptMap.containsKey(employee.getDeptId()) ? deptMap.get(employee.getDeptId()).getName() : null,
                        birthdayDate)).toList();
        return new HrmBirthdayCareDispatch(birthdayDate, today.atTime(config.triggerTime()), employees,
                getRecipientUserIds(deptIds));
    }

    @Override
    public List<Long> getMissingTaskPermissionUserIds() {
        List<Long> recipients = getRecipientUserIds(expandDeptIds(getConfig()));
        Set<Long> permitted = permissionApi.getEnabledUserIdsByPermission(TASK_PERMISSION);
        return recipients.stream().filter(userId -> !permitted.contains(userId)).sorted().toList();
    }

    public List<Long> getRecipientUserIds(Collection<Long> deptIds) {
        if (CollUtil.isEmpty(deptIds)) return List.of();
        return adminUserApi.getUserListByDeptIds(deptIds).stream()
                .filter(user -> user.getStatus() != null && user.getStatus() == 0)
                .map(AdminUserRespDTO::getId).filter(Objects::nonNull).distinct().sorted().toList();
    }

    private Set<Long> expandDeptIds(HrmBirthdayCareConfig config) {
        Set<Long> result = new LinkedHashSet<>(config.deptIds());
        if (config.includeChildDepartments()) {
            result.addAll(deptApi.getChildDeptList(config.deptIds()).stream().map(DeptRespDTO::getId).toList());
        }
        return result;
    }

    private HrmBirthdayCareConfig normalize(HrmBirthdayCareConfig config) {
        if (config == null) return HrmBirthdayCareConfig.defaults();
        int days = Math.max(0, Math.min(30, config.advanceDays()));
        LocalTime time = config.triggerTime() == null ? LocalTime.of(9, 0) : config.triggerTime().withSecond(0).withNano(0);
        List<Long> deptIds = config.deptIds() == null ? List.of() : config.deptIds().stream()
                .filter(Objects::nonNull).distinct().toList();
        return new HrmBirthdayCareConfig(config.enabled(), days, time, deptIds, config.includeChildDepartments());
    }
}
