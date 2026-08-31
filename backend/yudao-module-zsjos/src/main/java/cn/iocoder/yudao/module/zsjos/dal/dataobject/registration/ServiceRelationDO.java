package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.time.LocalDate;

@TableName("zsjos_service_relation")
@KeySequence("zsjos_service_relation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceRelationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long personId;
    private Long orderId;
    private Long orderItemId;
    private Long registrationCaseId;
    private String status;
    private Long ownerUserId;
    private String acceptanceStatus;
    private Long acceptedByUserId;
    private LocalDateTime acceptedAt;
    private Long contentDirectorUserId;
    private Long careerPlannerUserId;
    /** One operator owns the student relationship; account workflows keep independent states. */
    private Long operatorUserId;
    /** Director-owned student-level stage: precheck -> interview -> positioning_ready. */
    private String directorStage;
    private LocalDateTime directorInterviewAt;
    private Long directorFormConfigId;
    private Integer directorFormConfigVersion;
    private String directorPrecheckDraftJson;
    private Integer directorPrecheckDraftVersion;
    private String directorPrecheckSnapshotJson;
    private String directorInterviewDraftJson;
    private Integer directorInterviewDraftVersion;
    private String directorInterviewSnapshotJson;
    private String serviceSnapshot;
    /** Current delivery stage is owned by the service relation, not inferred from labels or contact text. */
    private String deliveryStage;
    private String deliveryDataJson;
    private LocalDate examDate;
    private Integer examDateVersion;
    private LocalDate lastNotifiedExamDate;
    private LocalDateTime examNoticeSentAt;
    private LocalDateTime activatedAt;
    private LocalDateTime pausedAt;
    private String pauseReason;
    private LocalDateTime completedAt;
    private LocalDateTime terminatedAt;
    private String terminationReason;
    private Integer version;
}
