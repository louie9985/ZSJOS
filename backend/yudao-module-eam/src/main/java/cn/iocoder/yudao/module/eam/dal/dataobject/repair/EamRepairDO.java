package cn.iocoder.yudao.module.eam.dal.dataobject.repair;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * EAM 维修记录 DO
 */
@TableName("eam_repair")
@KeySequence("eam_repair_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamRepairDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 资产编号
     */
    private Long assetId;
    /**
     * 故障描述
     */
    private String faultDesc;
    /**
     * 维修方
     */
    private String repairVendor;
    /**
     * 维修费用
     */
    private BigDecimal cost;
    /**
     * 送修时间
     */
    private LocalDateTime startTime;
    /**
     * 完成时间，空表示维修中
     */
    private LocalDateTime endTime;
    /**
     * 维修结果
     */
    private String result;

}
