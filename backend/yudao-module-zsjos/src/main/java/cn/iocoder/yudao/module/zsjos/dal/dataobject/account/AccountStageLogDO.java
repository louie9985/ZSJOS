package cn.iocoder.yudao.module.zsjos.dal.dataobject.account;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("zsjos_account_stage_log")
@KeySequence("zsjos_account_stage_log_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AccountStageLogDO extends TenantBaseDO {
    @TableId private Long id;
    private Long accountId;
    private String fromStage;
    private String toStage;
    private String stageVersion;
    private String direction;
    private String criteriaSnapshotJson;
    private String judgmentBasis;
    private Long judgedByUserId;
    private LocalDateTime judgedAt;
    private String idempotencyKey;
}
