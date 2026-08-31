package cn.iocoder.yudao.module.hrm.controller.admin.birthdaycare.vo;

import lombok.Data;

import java.util.List;

@Data
public class HrmBirthdayCareConfigRespVO {
    private Boolean enabled;
    private Integer advanceDays;
    private String triggerTime;
    private List<Long> deptIds;
    private Boolean includeChildDepartments;
    private List<Long> recipientUserIds;
    private List<Long> missingTaskPermissionUserIds;
}
