package cn.iocoder.yudao.module.zsjos.dal.dataobject.content;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_content_version")
@KeySequence("zsjos_content_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long contentId;
    private Integer versionNo;
    private String stage;
    private String titleSnapshot;
    private String topicSnapshot;
    private String coverSnapshotJson;
    private String materialRefsJson;
    private Long referenceContentVersionId;
    /** 参考作品链接，与素材库引用相互独立，可同时填写。 */
    private String referenceWorkUrl;
    private String deliverableUrl;
    private String deliverableSnapshotJson;
    private String scriptText;
    private String purposeValue;
    private String purposeLabelSnapshot;
    private String formatValue;
    private String formatLabelSnapshot;
    private String detailUrl;
    private String commentHook;
    private String leadResourceUrl;
    private LocalDateTime plannedPublishAt;
    private LocalDateTime frozenAt;
    private Long submittedByUserId;
    private LocalDateTime submittedAt;
    private String reviewDecision;
    private String reviewComment;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private String idempotencyKey;
}


