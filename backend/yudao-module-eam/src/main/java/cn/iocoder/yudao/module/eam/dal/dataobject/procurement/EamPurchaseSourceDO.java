package cn.iocoder.yudao.module.eam.dal.dataobject.procurement;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("eam_purchase_source")
@KeySequence("eam_purchase_source_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamPurchaseSourceDO extends BaseDO {
    @TableId
    private Long id;
    private Long purchaseItemId;
    private Long demandItemId;
    private Integer quantity;
    private Integer fulfilledQuantity;
    private Integer closedQuantity;
    private Long targetEmployeeId;
    private Long targetDeptId;
}
