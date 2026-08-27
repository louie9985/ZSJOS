package cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_feedback_config")
@KeySequence("zsjos_feedback_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackConfigDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String feedbackType;
    private Long formId;
    private String titleFieldKey;
    private String dispatcherUserIdsJson;
    private Boolean approvalEnabled;
    private String bpmProcessDefinitionKey;
    private String lastIdempotencyKey;
    private String lastRequestFingerprint;
    @Version
    private Integer version;
}
