package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import lombok.Data;

import java.util.List;

@Data
public class WorkPlanTemplateRespVO {
    private Long id;
    private Long typeId;
    private String code;
    private String name;
    private String description;
    private String status;
    private Integer currentVersionNo;
    private Long versionId;
    private String versionStatus;
    private String periodMode;
    private List<WorkPlanTemplateFieldSaveReqVO> fields;
    private List<Long> applicableDeptIds;
    private Boolean includeChildDepartments;
    private List<WorkPlanTemplateItemSaveReqVO> presetItems;
}
