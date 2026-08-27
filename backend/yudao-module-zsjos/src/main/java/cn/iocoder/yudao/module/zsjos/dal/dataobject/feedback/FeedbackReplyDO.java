package cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_feedback_reply")
@KeySequence("zsjos_feedback_reply_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackReplyDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long feedbackId;
    private Long authorUserId;
    private String authorNameSnapshot;
    private String authorType;
    private String content;
    private String attachmentIdsJson;
    private String idempotencyKey;
}
