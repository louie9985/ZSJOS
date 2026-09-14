package cn.iocoder.yudao.module.zsjos.controller.admin.positioninginterview.vo;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.*;
import java.time.LocalDateTime;
public final class PositioningInterviewVO {
 private PositioningInterviewVO() {}
 @Data public static class Item {
  @NotBlank private String fieldKey;
  private String status;
  private String value;
  @Size(max=4000) private String remark;
 }
 @Data public static class SaveReq {
  private Long studentPersonId;
  private Long serviceRelationId;
  @NotNull @Min(0) private Integer version;
  @NotBlank @Size(max=100) private String idempotencyKey;
  @NotNull private Long templateVersionId;
  private String collectedAt;
  @NotNull @Size(max=200) private List<@Valid Item> items;
  @NotNull @Size(max=20) private List<Long> attachmentIds;
 }
 @Data public static class Attachment {
  private Long fileId; private String fileName; private String mimeType; private Long fileSize; private String url;
 }
 @Data public static class Context {
  private Long id; private Long studentPersonId; private Long serviceRelationId;
  private String studentName; private String studentNo;
  private String status; private Integer version;
  private Long templateId; private Long templateVersionId;
  private List<DirectorFormTemplateVO.Field> fields;
  private List<Item> items;
  private List<Attachment> attachments;
  private Map<String,String> statusOptions;
  private List<String> availableActions;
  private String collectedAt; private LocalDateTime interviewAt; private LocalDateTime completedAt;
  private String legacyInterviewSnapshotJson;
 }
}
