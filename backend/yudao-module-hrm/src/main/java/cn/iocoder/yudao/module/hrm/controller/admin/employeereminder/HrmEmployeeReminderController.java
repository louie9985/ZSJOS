package cn.iocoder.yudao.module.hrm.controller.admin.employeereminder;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.hrm.api.employeereminder.*;
import cn.iocoder.yudao.module.hrm.controller.admin.employeereminder.vo.*;
import cn.iocoder.yudao.module.hrm.service.employeereminder.HrmEmployeeReminderService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 员工提醒")
@RestController @RequestMapping({"/hrm/employee-reminder", "/hrm/employee-reminder-config"}) @Validated
public class HrmEmployeeReminderController {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("HH:mm");
    @Resource private HrmEmployeeReminderService service; @Resource private DeptApi deptApi;
    @GetMapping({"", "/config"}) @Operation(summary = "获得员工提醒配置") @PreAuthorize("@ss.hasPermission('hrm:employee-reminder-config:query') or @ss.hasPermission('hrm:birthday-care-config:query')")
    public CommonResult<HrmEmployeeReminderConfigRespVO> getConfig() { return success(toResp(service.getConfig())); }
    @PutMapping({"", "/config"}) @Operation(summary = "保存员工提醒配置") @PreAuthorize("@ss.hasPermission('hrm:employee-reminder-config:update') or @ss.hasPermission('hrm:birthday-care-config:update')")
    public CommonResult<Boolean> saveConfig(@Valid @RequestBody HrmEmployeeReminderConfigSaveReqVO req) {
        service.saveConfig(new HrmEmployeeReminderConfig(toRule(req.getBirthday()), toRule(req.getContractExpiry()), toRule(req.getEntryAnniversary()))); return success(true);
    }
    private HrmEmployeeReminderRule toRule(HrmEmployeeReminderRuleVO v) { LocalTime t; try { t = LocalTime.parse(v.getTriggerTime(), F); } catch (Exception e) { throw new IllegalArgumentException("触发时间格式必须为 HH:mm"); } if (Boolean.TRUE.equals(v.getEnabled()) && (v.getDeptIds() == null || v.getDeptIds().isEmpty())) throw new IllegalArgumentException("启用员工提醒时必须选择部门"); if (v.getDeptIds() != null && !v.getDeptIds().isEmpty()) deptApi.validateDeptList(v.getDeptIds()); return new HrmEmployeeReminderRule(Boolean.TRUE.equals(v.getEnabled()), v.getAdvanceDays() == null ? 1 : v.getAdvanceDays(), t, v.getDeptIds(), Boolean.TRUE.equals(v.getIncludeChildDepartments())); }
    private HrmEmployeeReminderConfigRespVO toResp(HrmEmployeeReminderConfig c) { HrmEmployeeReminderConfigRespVO r = new HrmEmployeeReminderConfigRespVO(); r.setBirthday(rule(c.birthday())); r.setContractExpiry(rule(c.contractExpiry())); r.setEntryAnniversary(rule(c.entryAnniversary())); return r; }
    private HrmEmployeeReminderConfigRespVO.HrmEmployeeReminderRuleRespVO rule(HrmEmployeeReminderRule x) { var r = new HrmEmployeeReminderConfigRespVO.HrmEmployeeReminderRuleRespVO(); r.setEnabled(x.enabled()); r.setAdvanceDays(x.advanceDays()); r.setTriggerTime(x.triggerTime().format(F)); r.setDeptIds(x.deptIds()); r.setIncludeChildDepartments(x.includeChildDepartments()); r.setRecipientUserIds(service.getRecipientUserIds(x.deptIds())); r.setMissingTaskPermissionUserIds(service.getMissingTaskPermissionUserIds(x)); return r; }
}
