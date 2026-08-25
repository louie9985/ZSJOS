package cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("zsjos_positioning_card_submission")
@KeySequence("zsjos_positioning_card_submission_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class PositioningCardSubmissionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long cardId;
    private Long accountId;
    private Long studentPersonId;
    private Long serviceRelationId;
    private Integer submissionNo;
    private Long directorUserId;
    private Long operatorUserId;
    private Long templateId;
    private Long templateVersionId;
    private String fieldsSnapshotJson;
    private String valuesSnapshotJson;
    private String dictSnapshotJson;
    private String layer1Json;
    private String layer2Json;
    private String formulaJson;
    private String feasibilityJson;
    private String contentFormJson;
    private String complianceJson;
    private LocalDate trialEndDate;
    private Boolean professionalRisk;
    private String status;
    private LocalDateTime submittedAt;
    private Long operatorReviewedByUserId;
    private LocalDateTime operatorReviewedAt;
    private String operatorReviewComment;
    private String studentDecision;
    private String studentDecisionComment;
    private LocalDateTime studentDecidedAt;
    private Integer version;
}
