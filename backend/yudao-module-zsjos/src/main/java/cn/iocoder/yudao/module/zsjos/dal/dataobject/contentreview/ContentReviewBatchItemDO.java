package cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_content_review_batch_item")
@KeySequence("zsjos_content_review_batch_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentReviewBatchItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long batchId;
    private Long contentId;
    private Long contentVersionId;
    private Integer sortNo;
    private String contentSnapshotJson;
    private String directorDecision;
    private String directorComment;
    private Long directorReviewedByUserId;
    private LocalDateTime directorReviewedAt;
    private String finalDecision;
    private String finalComment;
    private Boolean collectMaterial;
    private String collectionSnapshotJson;
    private Long finalReviewedByUserId;
    private LocalDateTime finalReviewedAt;
    private Long collectedMaterialId;
    private Long collectedMaterialVersionId;
    private String resultStatus;
    private String publishedPlatformUrl;
    private LocalDateTime publishedAt;
    private Long publishedByUserId;
    private Integer version;
}
