package cn.iocoder.yudao.module.eam.dal.dataobject.scrap;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * EAM 报废单 DO
 */
@TableName("eam_scrap")
@KeySequence("eam_scrap_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamScrapDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 单据编号
     */
    private String no;
    /**
     * 资产编号
     */
    private Long assetId;
    /**
     * 报废原因类型，字典 eam_scrap_reason
     */
    private Integer reasonType;
    /**
     * 详细原因
     */
    private String reason;
    /**
     * 报废日期
     */
    private LocalDate scrapDate;
    /**
     * 状态：0 审批中 / 1 已报废 / 2 已驳回
     */
    private Integer status;
    /**
     * BPM 流程实例 ID
     */
    private String processInstanceId;
    /**
     * 申请人
     */
    private Long applyUserId;
    /**
     * 申请时间
     */
    private LocalDateTime applyTime;

}
