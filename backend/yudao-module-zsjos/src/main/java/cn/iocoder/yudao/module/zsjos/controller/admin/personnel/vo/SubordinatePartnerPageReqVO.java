package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SubordinatePartnerPageReqVO extends PageParam {
    private String keyword;
    private String status;
}
