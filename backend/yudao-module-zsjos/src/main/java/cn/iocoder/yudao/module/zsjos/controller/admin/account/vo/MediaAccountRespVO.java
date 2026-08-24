package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class MediaAccountRespVO {
    private Long id;
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
    private Map<String, Object> detailValues;
    private List<MediaAccountDetailSnapshotVO> detailSnapshots;
    private String leadDirection;
    private String sStage;
    private LocalDateTime sStageEnteredAt;
    private String runStatus;
    private String rescueStatus;
    private String rebindProcessInstanceId;
    private Long rebindTargetStudentPersonId;
    private Long rebindReviewerUserId;
    private String rebindStatus;
    private String rebindResultReason;
    private Integer version;
    private List<String> availableActions;
}
