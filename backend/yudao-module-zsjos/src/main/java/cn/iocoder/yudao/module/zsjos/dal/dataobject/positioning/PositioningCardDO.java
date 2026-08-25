package cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.time.LocalDate;

@TableName("zsjos_positioning_card")
@KeySequence("zsjos_positioning_card_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class PositioningCardDO extends TenantBaseDO {
    @TableId private Long id;
    private String cardNo;
    private Long accountId;
    private Long studentPersonId;
    private Long serviceRelationId;
    private Long directorUserId;
    private Long operatorUserId;
    private Long templateId;
    private Long templateVersionId;
    private String fieldsSnapshotJson;
    private String valuesSnapshotJson;
    private String dictSnapshotJson;
    private LocalDate trialEndDate;
    private Integer versionNo;
    private String layer1Json;
    private String layer2Json;
    private String formulaJson;
    private String feasibilityJson;
    private String contentFormJson;
    private String complianceJson;
    private Boolean professionalRisk;
    private String status;
    private String ipProcessInstanceId;
    private Long ipReviewerUserId;
    private LocalDateTime ipReviewedAt;
    private Long operatorReviewedByUserId;
    private LocalDateTime operatorReviewedAt;
    private String operatorReviewComment;
    private Integer version;
}
