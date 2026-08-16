package cn.iocoder.yudao.module.eam.dal.dataobject.transfer;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.eam.enums.transfer.EamTransferStatusEnum;
import cn.iocoder.yudao.module.eam.enums.transfer.EamTransferTypeEnum;
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
 * EAM 流转单 DO
 */
@TableName("eam_transfer")
@KeySequence("eam_transfer_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamTransferDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 单据编号
     */
    private String no;
    /**
     * 流转类型
     *
     * 枚举 {@link EamTransferTypeEnum}
     */
    private Integer type;
    /**
     * 资产编号
     */
    private Long assetId;
    /**
     * 转出使用人
     */
    private Long fromUserId;
    /**
     * 转出部门
     */
    private Long fromDeptId;
    /**
     * 接收使用人
     */
    private Long toUserId;
    /**
     * 接收部门
     */
    private Long toDeptId;
    /**
     * 预计归还日期（仅借用）
     */
    private LocalDate expectedReturnDate;
    /**
     * 实际归还日期（仅归还时填写）
     */
    private LocalDate actualReturnDate;
    /**
     * 单据状态
     *
     * 枚举 {@link EamTransferStatusEnum}
     */
    private Integer status;
    /**
     * BPM 流程实例 ID
     */
    private String processInstanceId;
    /**
     * 事由
     */
    private String reason;
    /**
     * 申请人
     */
    private Long applyUserId;
    /**
     * 申请时间
     */
    private LocalDateTime applyTime;

}
