package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;

@Data
public class MediaStudentDetailRespVO {
    private MyStudentRespVO student;
    private List<AccountVO> accounts;
    private List<PositioningVO> positioningCards;
    private List<ContentVO> contents;
    private List<TicketVO> productionTickets;

    @Data
    public static class AccountVO {
        private Long id;
        private String accountNo;
        private String nickname;
        private String platformLabel;
        private String stage;
        private String runStatus;
        private Integer version;
        private List<String> availableActions;
        private List<MediaAccountDetailSnapshotVO> detailSnapshots;
    }

    @Data
    public static class PositioningVO {
        private Long id;
        private Long accountId;
        private String cardNo;
        private String status;
        private Integer versionNo;
        private Boolean professionalRisk;
        private Integer version;
        private List<String> availableActions;
    }

    @Data
    public static class ContentVO {
        private Long id;
        private Long accountId;
        private String contentNo;
        private String title;
        private String status;
        private Integer currentVersionNo;
        private LocalDateTime publishedAt;
        private Integer version;
        private List<String> availableActions;
    }

    @Data
    public static class TicketVO {
        private Long id;
        private Long accountId;
        private String ticketNo;
        private String status;
        private LocalDateTime deadlineAt;
        private Integer revisionCount;
    }
}
