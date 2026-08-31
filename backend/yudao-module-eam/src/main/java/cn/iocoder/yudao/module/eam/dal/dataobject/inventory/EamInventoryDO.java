package cn.iocoder.yudao.module.eam.dal.dataobject.inventory;

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

import java.time.LocalDateTime;

/**
 * EAM 盘点单 DO
 */
@TableName("eam_inventory")
@KeySequence("eam_inventory_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamInventoryDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 盘点单号
     */
    private String no;
    /**
     * 盘点名称
     */
    private String name;
    /**
     * 范围类型：1 全部 / 2 按部门 / 3 按分类 / 4 按存放地点
     */
    private Integer scopeType;
    /**
     * 范围值（逗号分隔的 ID 或地点名）
     */
    private String scopeValue;
    /**
     * 盘点状态：0 进行中 / 1 已完成
     */
    private Integer status;
    /**
     * 应盘数量
     */
    private Integer totalCount;
    /**
     * 已盘数量
     */
    private Integer checkedCount;
    /**
     * 正常数量
     */
    private Integer normalCount;
    /**
     * 异常数量
     */
    private Integer abnormalCount;
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    /**
     * 备注
     */
    private String remark;

}
