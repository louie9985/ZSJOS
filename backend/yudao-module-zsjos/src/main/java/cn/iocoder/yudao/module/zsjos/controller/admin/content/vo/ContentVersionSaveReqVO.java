package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentVersionSaveReqVO {
    @NotNull private Long contentId;
    /** Deprecated input retained for wire compatibility; the server derives stage from the content state. */
    private String stage;
    @Size(max = 255) private String titleSnapshot;
    @Size(max = 1000) private String topicSnapshot;
    private String coverSnapshotJson;
    private String materialRefsJson;
    private String deliverableUrl;
    private String deliverableSnapshotJson;
    private String scriptText;
    private String leadResourceUrl;
    private LocalDateTime plannedPublishAt;
    @Size(max = 128) private String idempotencyKey;
}
