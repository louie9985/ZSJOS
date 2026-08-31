package cn.iocoder.yudao.module.zsjos.dal.dataobject.account;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("zsjos_media_account_maintenance_revision")
@KeySequence("zsjos_media_account_maintenance_revision_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class MediaAccountMaintenanceRevisionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long accountId;
    private Integer revisionNo;
    private String currentStatusValue;
    private String currentStatusLabelSnapshot;
    private String stageValue;
    private String stageLabelSnapshot;
    private String primaryProblemsJson;
    private String executionMeasureValue;
    private String executionMeasureLabelSnapshot;
    private String adjustmentDirection;
    private LocalDate startDate;
    private LocalDate endDate;
    private String changedFieldsJson;
    private Long operatedByUserId;
    private LocalDateTime operatedAt;
}
