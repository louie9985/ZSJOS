package cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_feedback_survey")
@KeySequence("zsjos_feedback_survey_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackSurveyDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long feedbackId;
    private String status;
    private Long formId;
    private String formSnapshotJson;
    private String valueSnapshotJson;
    private Long requestedByUserId;
    private String requestedByNameSnapshot;
    private LocalDateTime requestedAt;
    private Long submitterUserId;
    private LocalDateTime submittedAt;
}
