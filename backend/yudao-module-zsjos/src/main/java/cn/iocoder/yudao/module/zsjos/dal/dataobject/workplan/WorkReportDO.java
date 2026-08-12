package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_work_report")
@KeySequence("zsjos_work_report_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkReportDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private Integer revisionNo;
    private String completionSummary;
    private Long submitterUserId;
    private LocalDateTime submittedAt;
    private String confirmationDecision;
    private String confirmationComment;
    private Long confirmedByUserId;
    private LocalDateTime confirmedAt;
}
