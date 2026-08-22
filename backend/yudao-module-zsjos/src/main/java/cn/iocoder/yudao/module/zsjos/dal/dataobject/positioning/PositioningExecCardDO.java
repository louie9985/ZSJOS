package cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_positioning_exec_card")
@KeySequence("zsjos_positioning_exec_card_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PositioningExecCardDO extends TenantBaseDO {
    @TableId private Long id;
    private String execCardNo;
    private Long accountId;
    private Long positioningCardId;
    private String executionJson;
    private LocalDateTime reviewDueAt;
    private Integer allowedRepositionCount;
    private LocalDateTime studentConfirmedAt;
    private LocalDateTime directorConfirmedAt;
    private LocalDateTime operatorConfirmedAt;
    private String signatureSnapshotJson;
    private LocalDateTime effectiveAt;
    private String status;
    private Integer version;
}
