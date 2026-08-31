package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ContentRespVO {
    private Long id;
    private String contentNo;
    private Long accountId;
    private Long productionTicketId;
    private String title;
    private String topic;
    private String contentClassValue;
    private String contentClassLabelSnapshot;
    private String status;
    private Integer currentVersionNo;
    private String scriptText;
    private String scriptUrl;
    private Long ownerOperatorUserId;
    private Long filmingEditorUserId;
    private String publishedUrl;
    private LocalDateTime publishedAt;
    private Integer rejectCount;
    private Integer version;
    private List<String> availableActions;
}
