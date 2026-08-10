package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 派单关系分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAssignmentRelationPageReqVO extends PageParam {

    private String keyword;
    private Long deptId;
    private Boolean configured;

}
