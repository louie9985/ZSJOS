package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadComplaintPageReqVO extends PageParam {
    private String status;
}
