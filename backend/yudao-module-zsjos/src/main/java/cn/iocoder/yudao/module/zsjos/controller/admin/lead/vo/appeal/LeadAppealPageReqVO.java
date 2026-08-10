package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAppealPageReqVO extends PageParam {
    private Boolean handled = false;
}
