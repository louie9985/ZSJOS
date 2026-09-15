package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/** 列表和选择器只返回展示字段，不能复用携带商户密钥的详情响应。 */
@Data
@Schema(description = "管理后台 - 支付主体列表项")
public class PaymentSubjectSummaryRespVO {
    private Long id;
    private String subjectCode;
    private String subjectName;
    private String cusid;
    private Integer status;
    private LocalDateTime createTime;
}
