package cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_feedback_round")
@KeySequence("zsjos_feedback_round_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackRoundDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long feedbackId;
    private Integer roundNo;
    private String status;
    private String formSnapshotJson;
    private String valueSnapshotJson;
    private String approvalContextJson;
    private String processInstanceId;
    private String businessKey;
    private String rejectReason;
    private LocalDateTime submittedAt;
}
