package cn.iocoder.yudao.module.eam.dal.dataobject.inventory;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.eam.enums.inventory.EamInventoryResultEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * EAM 盘点明细 DO
 */
@TableName("eam_inventory_detail")
@KeySequence("eam_inventory_detail_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamInventoryDetailDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 盘点单编号
     */
    private Long inventoryId;
    /**
     * 资产编号
     */
    private Long assetId;
    /**
     * 账面使用人
     */
    private Long expectUserId;
    /**
     * 账面使用部门
     */
    private Long expectDeptId;
    /**
     * 账面存放地点
     */
    private String expectLocation;
    /**
     * 实盘使用人
     */
    private Long actualUserId;
    /**
     * 实盘使用部门
     */
    private Long actualDeptId;
    /**
     * 实盘存放地点
     */
    private String actualLocation;
    /**
     * 盘点结果
     *
     * 枚举 {@link EamInventoryResultEnum}
     */
    private Integer result;
    /**
     * 备注
     */
    private String remark;
    /**
     * 盘点人
     */
    private Long checkUserId;
    /**
     * 盘点时间
     */
    private LocalDateTime checkTime;

}
