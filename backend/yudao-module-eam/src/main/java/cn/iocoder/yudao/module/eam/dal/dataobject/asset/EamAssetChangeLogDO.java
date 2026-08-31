package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
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
 * EAM 资产变更记录 DO
 */
@TableName("eam_asset_change_log")
@KeySequence("eam_asset_change_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamAssetChangeLogDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 资产编号
     */
    private Long assetId;
    /**
     * 变更类型
     *
     * 枚举 {@link EamChangeTypeEnum}
     */
    private Integer changeType;
    /**
     * 变更前状态
     */
    private Integer beforeStatus;
    /**
     * 变更后状态
     */
    private Integer afterStatus;
    /**
     * 变更前使用人
     */
    private Long beforeEmployeeId;
    /**
     * 变更后使用人
     */
    private Long afterEmployeeId;
    /**
     * 变更前使用部门
     */
    private Long beforeDeptId;
    /**
     * 变更后使用部门
     */
    private Long afterDeptId;
    /**
     * 关联单据 ID（流转单/盘点单等），可空
     */
    private Long bizId;
    /**
     * 变更描述文本
     */
    private String content;
    /**
     * 操作人
     */
    private Long operatorId;
    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

}
