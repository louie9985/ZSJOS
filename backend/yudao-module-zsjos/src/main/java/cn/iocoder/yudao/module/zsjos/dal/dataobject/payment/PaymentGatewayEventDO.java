package cn.iocoder.yudao.module.zsjos.dal.dataobject.payment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@TableName("zsjos_payment_gateway_event")
@KeySequence("zsjos_payment_gateway_event_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class PaymentGatewayEventDO extends TenantBaseDO {
    @TableId private Long id;
    private String eventId;
    private Long paymentOrderId;
    private String eventType;
    private String requestPayload;
    private String responsePayload;
    private Boolean signatureValid;
    private String processingResult;
    private String businessType;
    private String operation;
    private String direction;
    private Long businessId;
    private String requestNo;
    private Integer httpStatus;
    private String gatewayRetcode;
    private String gatewayTrxstatus;
    private String errorCategory;
    private String payloadDigest;
}
