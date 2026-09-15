package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductPaymentSubjectPageReqVO extends PageParam {
    private String productName;
    private Long paymentSubjectId;
}
