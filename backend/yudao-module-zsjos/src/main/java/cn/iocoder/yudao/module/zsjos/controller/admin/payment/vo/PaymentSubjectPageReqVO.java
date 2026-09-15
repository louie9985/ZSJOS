package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentSubjectPageReqVO extends PageParam {
    private String subjectName;
    private Integer status;
}
