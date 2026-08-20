package cn.iocoder.yudao.module.hrm.controller.admin.birthdaycare;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.hrm.api.birthdaycare.HrmBirthdayCareConfig;
import cn.iocoder.yudao.module.hrm.controller.admin.birthdaycare.vo.HrmBirthdayCareConfigRespVO;
import cn.iocoder.yudao.module.hrm.controller.admin.birthdaycare.vo.HrmBirthdayCareConfigSaveReqVO;
import cn.iocoder.yudao.module.hrm.service.birthdaycare.HrmBirthdayCareService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 生日关怀")
@RestController
@RequestMapping({"/hrm/birthday-care", "/hrm/birthday-care-config"})
@Validated
public class HrmBirthdayCareController {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    @Resource private HrmBirthdayCareService birthdayCareService;
    @Resource private DeptApi deptApi;

    @GetMapping({"", "/config"})
    @Operation(summary = "获得生日关怀配置")
    @PreAuthorize("@ss.hasPermission('hrm:birthday-care-config:query')")
    public CommonResult<HrmBirthdayCareConfigRespVO> getConfig() {
        return success(toResp(birthdayCareService.getConfig()));
    }

    @PutMapping({"", "/config"})
    @Operation(summary = "保存生日关怀配置")
    @PreAuthorize("@ss.hasPermission('hrm:birthday-care-config:update')")
    public CommonResult<Boolean> saveConfig(@Valid @RequestBody HrmBirthdayCareConfigSaveReqVO reqVO) {
        LocalTime time;
        try {
            time = LocalTime.parse(reqVO.getTriggerTime(), TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("触发时间格式必须为 HH:mm");
        }
        if (Boolean.TRUE.equals(reqVO.getEnabled()) && (reqVO.getDeptIds() == null || reqVO.getDeptIds().isEmpty())) {
            throw new IllegalArgumentException("启用生日关怀时必须选择部门");
        }
        if (reqVO.getDeptIds() != null && !reqVO.getDeptIds().isEmpty()) deptApi.validateDeptList(reqVO.getDeptIds());
        birthdayCareService.saveConfig(new HrmBirthdayCareConfig(Boolean.TRUE.equals(reqVO.getEnabled()),
                reqVO.getAdvanceDays(), time, reqVO.getDeptIds(), Boolean.TRUE.equals(reqVO.getIncludeChildDepartments())));
        return success(true);
    }

    private HrmBirthdayCareConfigRespVO toResp(HrmBirthdayCareConfig config) {
        HrmBirthdayCareConfigRespVO vo = new HrmBirthdayCareConfigRespVO();
        vo.setEnabled(config.enabled()); vo.setAdvanceDays(config.advanceDays());
        vo.setTriggerTime(config.triggerTime().format(TIME_FORMAT)); vo.setDeptIds(config.deptIds());
        vo.setIncludeChildDepartments(config.includeChildDepartments());
        vo.setRecipientUserIds(birthdayCareService.getRecipientUserIds(config.deptIds()));
        vo.setMissingTaskPermissionUserIds(birthdayCareService.getMissingTaskPermissionUserIds());
        return vo;
    }
}
