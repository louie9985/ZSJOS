package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WithdrawalPageReqVO extends PageParam {
    private String status;
}
