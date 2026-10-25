package cn.iocoder.yudao.module.zsjos.dal.dataobject.account;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data @EqualsAndHashCode(callSuper=true)
@TableName("zsjos_account_diagnosis_metric")
public class DiagnosisMetricDO extends TenantBaseDO {
    @TableId private Long id;
    private Long accountId;
    private String nodeKey;
    private String roundKey;
    private Integer intervalDays;
    private Integer intervalCycle;
    private LocalDateTime periodFrom;
    private LocalDateTime periodTo;
    private Long partnerId;
    private Long partnerLinkId;
    private String state;
    private String metricJson;
    private String submissionSnapshotJson;
    private String failureReason;
    private LocalDateTime settledAt;
}
