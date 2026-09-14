package cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_content_review_batch")
@KeySequence("zsjos_content_review_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentReviewBatchDO extends TenantBaseDO {
    @TableId private Long id;
    private String batchNo;
    private Long accountId;
    private Long studentPersonId;
    private Long revisionOfBatchId;
    private String accountIdsJson;
    private Long operatorUserId;
    private Long directorUserId;
    private String relationSnapshotJson;
    private String contextSnapshotJson;
    private String status;
    private String currentStage;
    private String processDefinitionId;
    private String processDefinitionKey;
    private Integer processDefinitionVersion;
    private String processInstanceId;
    private String businessKey;
    private String lastEventKey;
    private LocalDateTime submittedAt;
    private LocalDateTime directorCompletedAt;
    private LocalDateTime finalCompletedAt;
    private LocalDateTime finalizedAt;
    private Integer version;
}


