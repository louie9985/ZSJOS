package cn.iocoder.yudao.module.eam.dal.dataobject.stock;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("eam_stock_reservation")
@KeySequence("eam_stock_reservation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamStockReservationDO extends BaseDO {
    @TableId
    private Long id;
    private Long demandItemId;
    private Long stockBalanceId;
    private Long assetId;
    private Long targetEmployeeId;
    private Integer quantity;
    private Integer status;
}
