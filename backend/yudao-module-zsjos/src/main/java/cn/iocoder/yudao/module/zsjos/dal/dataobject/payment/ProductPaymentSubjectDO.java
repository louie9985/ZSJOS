package cn.iocoder.yudao.module.zsjos.dal.dataobject.payment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 产品支付主体关联 DO
 *
 * @author ZSJOS
 */
@TableName("zsjos_product_payment_subject")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPaymentSubjectDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 产品编号
     */
    private Long productId;

    /**
     * 支付主体编号
     */
    private Long paymentSubjectId;
}
