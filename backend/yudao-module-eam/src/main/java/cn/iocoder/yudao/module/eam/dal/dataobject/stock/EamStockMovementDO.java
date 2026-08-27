package cn.iocoder.yudao.module.eam.dal.dataobject.stock;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("eam_stock_movement")
@KeySequence("eam_stock_movement_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamStockMovementDO extends BaseDO {
    @TableId
    private Long id;
    private Long stockBalanceId;
    private Integer type;
    private Integer quantity;
    private Integer beforeQuantity;
    private Integer afterQuantity;
    private String businessType;
    private Long businessId;
    private Long operatorUserId;
    private LocalDateTime operateTime;
    private String remark;
}
