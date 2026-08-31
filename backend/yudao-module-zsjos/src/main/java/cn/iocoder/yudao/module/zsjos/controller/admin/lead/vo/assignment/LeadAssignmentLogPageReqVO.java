package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAssignmentLogPageReqVO extends PageParam {

    private String scene = "lead_specified_assignment";
    private String actionType;

}
