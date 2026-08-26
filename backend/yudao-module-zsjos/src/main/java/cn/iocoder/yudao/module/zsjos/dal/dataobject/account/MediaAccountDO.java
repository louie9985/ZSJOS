package cn.iocoder.yudao.module.zsjos.dal.dataobject.account;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.time.LocalDate;

@TableName("zsjos_media_account")
@KeySequence("zsjos_media_account_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class MediaAccountDO extends TenantBaseDO {
    @TableId private Long id;
    private String accountNo;
    private Long studentPersonId;
    private String ownershipType;
    private Long ownerOperatorUserId;
    private Long directorUserId;
    private String platformValue;
    private String platformLabelSnapshot;
    private String platformAccountId;
    private String nickname;
    private Long detailConfigVersionId;
    private String detailValuesJson;
    private String detailSnapshotJson;
    private String accountTypePrimaryValue;
    private String accountTypePrimaryLabelSnapshot;
    private String accountTypeSecondaryValue;
    private String accountTypeSecondaryLabelSnapshot;
    private String trackPrimaryValue;
    private String trackPrimaryLabelSnapshot;
    private String trackSecondaryValue;
    private String trackSecondaryLabelSnapshot;
    private String leadDirection;
    private String currentStatusValue;
    private String currentStatusLabelSnapshot;
    private String sStage;
    private String sStageLabelSnapshot;
    private String primaryProblemsJson;
    private String executionMeasureValue;
    private String executionMeasureLabelSnapshot;
    private String adjustmentDirection;
    private LocalDate maintenanceStartDate;
    private LocalDate maintenanceEndDate;
    private String sStageVersion;
    private LocalDateTime sStageEnteredAt;
    private Long sStageJudgedByUserId;
    private Boolean isSilent;
    private String coopLevelValue;
    private String coopLevelLabelSnapshot;
    private String accountGradeValue;
    private String accountGradeLabelSnapshot;
    private String healthStatusValue;
    private String healthStatusLabelSnapshot;
    private String primaryProblemCodeValue;
    private String primaryProblemCodeLabelSnapshot;
    private String secondaryProblemCodeValue;
    private String secondaryProblemCodeLabelSnapshot;
    private String runStatus;
    private String rescueStatus;
    private String whitelistStatus;
    private Long positioningCardId;
    private String contentModelJson;
    private String healthJson;
    private String riskLevelValue;
    private String riskLevelLabelSnapshot;
    private String rebindProcessInstanceId;
    private Long rebindTargetStudentPersonId;
    private Long rebindRequestedByUserId;
    private Long rebindReviewerUserId;
    private String rebindStatus;
    private String rebindResultReason;
    private Integer version;
}
