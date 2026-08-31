package cn.iocoder.yudao.module.eam.dal.dataobject.transfer;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.eam.enums.transfer.EamTransferStatusEnum;
import cn.iocoder.yudao.module.eam.enums.transfer.EamTransferTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * EAM 流转单 DO
 */
@TableName(value = "eam_transfer", autoResultMap = true)
@KeySequence("eam_transfer_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamTransferDO extends TenantBaseDO {

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
    private String assetCodeSnapshot;
    private String assetNameSnapshot;
    private String typeLabelSnapshot;
    /**
     * 转出使用人
     */
    private Long fromEmployeeId;
    /**
     * 转出部门
     */
    private Long fromDeptId;
    private String fromEmployeeNameSnapshot;
    private String fromDeptNameSnapshot;
    /**
     * 接收使用人
     */
    private Long toEmployeeId;
    /**
     * 接收部门
     */
    private Long toDeptId;
    private String toEmployeeNameSnapshot;
    private String toDeptNameSnapshot;
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
    private Integer roundNo;
    /**
     * 事由
     */
    private String reason;
    /**
     * 申请人
     */
    private Long applyUserId;
    private String applyUserNameSnapshot;
    private Long applyDeptId;
    private String applyDeptNameSnapshot;
    /**
     * 申请时间
     */
    private LocalDateTime applyTime;
    private Integer inspectionResult;
    private String inspectionRemark;
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> inspectionFileUrls;
    private Long inspectedByUserId;
    private LocalDateTime inspectedAt;
    @Version
    private Integer version;

}
