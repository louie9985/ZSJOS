package cn.iocoder.yudao.module.eam.dal.dataobject.stock;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("eam_stock_holding")
@KeySequence("eam_stock_holding_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamStockHoldingDO extends BaseDO {
    @TableId
    private Long id;
    private Long employeeId;
    private Long assetId;
    private Long stockBalanceId;
    private String nameSnapshot;
    private Integer quantity;
    private Integer custodyMode;
    private Integer status;
    private LocalDateTime signedAt;
    private LocalDateTime returnAppliedAt;
    private LocalDateTime returnInspectedAt;
    private Integer returnResult;
    private String returnRemark;
}
