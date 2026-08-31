package cn.iocoder.yudao.module.hrm.service.employeereminder;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.hrm.api.employeereminder.*;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.employment.HrmEmployeeContractMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import cn.iocoder.yudao.module.hrm.enums.config.HrmConfigTypeEnum;
import cn.iocoder.yudao.module.hrm.enums.employee.employment.HrmEmployeeContractStatusEnum;
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
import java.util.*;

@Service
public class HrmEmployeeReminderServiceImpl implements HrmEmployeeReminderService {
    private static final String TASK_PERMISSION = "zsjos:business-task:query";
    @Resource private HrmConfigService configService;
    @Resource private HrmEmployeeMapper employeeMapper;
    @Resource private HrmEmployeeContractMapper contractMapper;
    @Resource private DeptApi deptApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PermissionApi permissionApi;
    @Resource private ObjectMapper objectMapper;

    @Override public HrmEmployeeReminderConfig getConfig() {
        List<String> values = configService.getConfigValueList(HrmConfigTypeEnum.EMPLOYEE_REMINDER.getType());
        if (CollUtil.isEmpty(values)) {
            // Migrate the existing birthday-only configuration in memory.
            List<String> old = configService.getConfigValueList(HrmConfigTypeEnum.BIRTHDAY_CARE.getType());
            if (CollUtil.isEmpty(old)) return HrmEmployeeReminderConfig.defaults();
            try {
                HrmEmployeeReminderRule rule = objectMapper.readValue(old.get(0), HrmEmployeeReminderRule.class);
                return new HrmEmployeeReminderConfig(normalize(rule), HrmEmployeeReminderRule.defaults(), HrmEmployeeReminderRule.defaults());
            } catch (Exception ignored) { return HrmEmployeeReminderConfig.defaults(); }
        }
        try { return normalize(objectMapper.readValue(values.get(0), HrmEmployeeReminderConfig.class)); }
        catch (Exception ignored) { return HrmEmployeeReminderConfig.defaults(); }
    }

    @Override @Transactional public void saveConfig(HrmEmployeeReminderConfig config) {
        try { configService.replaceConfigValueList(HrmConfigTypeEnum.EMPLOYEE_REMINDER.getType(), List.of(objectMapper.writeValueAsString(normalize(config)))); }
        catch (Exception e) { throw new IllegalStateException("员工提醒配置保存失败", e); }
    }

    @Override public HrmEmployeeReminderDispatch getDueContractExpiryDispatch(LocalDate today, LocalTime now) {
        HrmEmployeeReminderRule rule = getConfig().contractExpiry();
        if (!due(rule, today, now)) return empty(rule, today);
        LocalDate target = today.plusDays(rule.advanceDays());
        Set<Long> deptIds = expand(rule); Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);
        List<HrmEmployeeReminderEmployee> list = contractMapper.selectReminderCandidates(HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES,
                        HrmEmployeeContractStatusEnum.IN_PROGRESS.getStatus(), target).stream().map(c -> {
                    Long employeeId = ((Number) c.get("employeeId")).longValue();
                    Long deptId = ((Number) c.get("deptId")).longValue();
                    Long contractId = ((Number) c.get("contractId")).longValue();
                    String name = (String) c.get("employeeName");
                    return new HrmEmployeeReminderEmployee(employeeId, name, deptId,
                            deptMap.containsKey(deptId) ? deptMap.get(deptId).getName() : null, target, contractId, null);
                }).filter(e -> deptIds.contains(e.deptId())).toList();
        return new HrmEmployeeReminderDispatch(target, today.atTime(rule.triggerTime()), list, getRecipientUserIds(deptIds));
    }

    @Override public HrmEmployeeReminderDispatch getDueBirthdayDispatch(LocalDate today, LocalTime now) {
        HrmEmployeeReminderRule rule = getConfig().birthday();
        if (!due(rule, today, now)) return empty(rule, today);
        LocalDate target = today.plusDays(rule.advanceDays());
        Set<Long> deptIds = expand(rule); Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);
        List<HrmEmployeeReminderEmployee> list = employeeMapper.selectBirthdayList(HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES, target).stream()
                .filter(e -> deptIds.contains(e.getDeptId()))
                .map(e -> new HrmEmployeeReminderEmployee(e.getId(), e.getName(), e.getDeptId(),
                        deptMap.containsKey(e.getDeptId()) ? deptMap.get(e.getDeptId()).getName() : null, target, null, null)).toList();
        return new HrmEmployeeReminderDispatch(target, today.atTime(rule.triggerTime()), list, getRecipientUserIds(deptIds));
    }

    @Override public HrmEmployeeReminderDispatch getDueEntryAnniversaryDispatch(LocalDate today, LocalTime now) {
        HrmEmployeeReminderRule rule = getConfig().entryAnniversary();
        if (!due(rule, today, now)) return empty(rule, today);
        LocalDate target = today.plusDays(rule.advanceDays());
        Set<Long> deptIds = expand(rule); Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);
        List<HrmEmployeeReminderEmployee> list = employeeMapper.selectEntryAnniversaryList(HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES, target).stream()
                .filter(e -> deptIds.contains(e.getDeptId()))
                .map(e -> new HrmEmployeeReminderEmployee(e.getId(), e.getName(), e.getDeptId(),
                        deptMap.containsKey(e.getDeptId()) ? deptMap.get(e.getDeptId()).getName() : null, target, null,
                        anniversaryYears(e.getEntryTime(), target))).toList();
        return new HrmEmployeeReminderDispatch(target, today.atTime(rule.triggerTime()), list, getRecipientUserIds(deptIds));
    }

    @Override public List<Long> getMissingTaskPermissionUserIds(HrmEmployeeReminderRule rule) {
        List<Long> recipients = getRecipientUserIds(expand(rule));
        Set<Long> permitted = permissionApi.getEnabledUserIdsByPermission(TASK_PERMISSION);
        return recipients.stream().filter(id -> !permitted.contains(id)).sorted().toList();
    }
    @Override public List<Long> getRecipientUserIds(Collection<Long> deptIds) {
        if (CollUtil.isEmpty(deptIds)) return List.of();
        return adminUserApi.getUserListByDeptIds(deptIds).stream().filter(u -> u.getStatus() != null && u.getStatus() == 0)
                .map(AdminUserRespDTO::getId).filter(Objects::nonNull).distinct().sorted().toList();
    }
    private Set<Long> expand(HrmEmployeeReminderRule rule) { Set<Long> ids = new LinkedHashSet<>(rule.deptIds()); if (rule.includeChildDepartments()) ids.addAll(deptApi.getChildDeptList(rule.deptIds()).stream().map(DeptRespDTO::getId).toList()); return ids; }
    private boolean due(HrmEmployeeReminderRule rule, LocalDate today, LocalTime now) { return rule.enabled() && !rule.deptIds().isEmpty() && !now.isBefore(rule.triggerTime()); }
    private HrmEmployeeReminderDispatch empty(HrmEmployeeReminderRule rule, LocalDate today) { return new HrmEmployeeReminderDispatch(today.plusDays(rule.advanceDays()), today.atTime(rule.triggerTime()), List.of(), List.of()); }
    private HrmEmployeeReminderRule normalize(HrmEmployeeReminderRule r) { if (r == null) return HrmEmployeeReminderRule.defaults(); return new HrmEmployeeReminderRule(r.enabled(), Math.max(0, Math.min(30, r.advanceDays())), r.triggerTime() == null ? LocalTime.of(9,0) : r.triggerTime().withSecond(0).withNano(0), r.deptIds() == null ? List.of() : r.deptIds().stream().filter(Objects::nonNull).distinct().toList(), r.includeChildDepartments()); }
    private HrmEmployeeReminderConfig normalize(HrmEmployeeReminderConfig c) { if (c == null) return HrmEmployeeReminderConfig.defaults(); return new HrmEmployeeReminderConfig(normalize(c.birthday()), normalize(c.contractExpiry()), normalize(c.entryAnniversary())); }
    private Integer anniversaryYears(LocalDateTime entry, LocalDate target) { return target.getYear() - entry.getYear(); }
}
