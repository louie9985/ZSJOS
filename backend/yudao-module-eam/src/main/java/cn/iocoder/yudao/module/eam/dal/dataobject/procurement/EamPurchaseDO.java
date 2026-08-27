package cn.iocoder.yudao.module.eam.dal.dataobject.procurement;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

@TableName(value = "eam_purchase", autoResultMap = true)
@KeySequence("eam_purchase_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamPurchaseDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    private Integer status;
    private Integer paymentMode;
    private String paymentModeLabelSnapshot;
    private String supplierNameSnapshot;
    private String supplierContactSnapshot;
    private BigDecimal estimatedAmount;
    private BigDecimal actualAmount;
    private LocalDate expectedArrivalDate;
    private String processInstanceId;
    private Integer expenseStatus;
    private String expenseProcessInstanceId;
    private Long applicantUserId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fileUrls;
    private String remark;
}
