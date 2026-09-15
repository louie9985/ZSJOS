package cn.iocoder.yudao.module.zsjos.dal.dataobject.payment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_payment_subject")
@KeySequence("zsjos_payment_subject_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentSubjectDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String subjectCode;
    private String subjectName;
    private String cusid;
    private String appid;
    private String orgid;
    private String merchantPrivateKey;
    private String platformPublicKey;
    private String rsaType;
    private Integer status;
    private Boolean isDefault;
    private Integer sort;
    private String remark;
}
