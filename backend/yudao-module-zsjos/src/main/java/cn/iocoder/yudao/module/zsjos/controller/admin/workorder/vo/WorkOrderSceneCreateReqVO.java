package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;

import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderFieldDefinition;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class WorkOrderSceneCreateReqVO {
    @NotBlank @Size(max = 64) private String code;
    @NotBlank @Size(max = 128) private String name;
    @Size(max = 500) private String remark;
    @NotBlank @Size(max = 100) private String categoryValue;
    @Size(max = 64) private String icon;
    @NotNull @Min(0) private Integer sort;
    @NotBlank @Size(max = 32) private String processorType;
    @NotNull @Size(min = 1, max = 2) private List<String> allowedAssignmentTypes;
    @NotBlank @Size(max = 32) private String sourceQualificationMode;
    @Size(max = 100) private List<Long> sourceRoleIds;
    @Size(max = 100) private List<Long> sourceDeptIds;
    @NotBlank @Size(max = 32) private String targetQualificationMode;
    @Size(max = 100) private List<Long> targetRoleIds;
    @Size(max = 100) private List<Long> targetDeptIds;
    @NotBlank @Size(max = 32) private String rejectionStrategy;
    @NotBlank @Size(min = 2, max = 12) private String numberPrefix;
    @NotBlank @Size(max = 16) private String numberResetPeriod;
    @NotNull @Min(4) @Max(8) private Integer numberSequenceWidth;
    @NotNull @Size(max = 100) private List<WorkOrderFieldDefinition> fields;
    /** Deprecated migration fields retained for existing scene clients. */
    private String sourcePostCode;
    private String targetPostCode;
    private String assignmentMode;
    @NotNull @Min(0) @Max(1) private Integer status;
}
