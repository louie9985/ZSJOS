package cn.iocoder.yudao.module.zsjos.controller.admin.versionupdate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.List;

public final class VersionUpdateVO {
    private VersionUpdateVO() {}
    @Data public static class Save {
        @NotBlank @Size(max=100) private String title;
        @NotBlank @Size(max=1000000) private String content;
        @NotEmpty @Size(max=10000) private List<@NotNull Long> targetUserIds;
        @NotNull @Size(max=10) private List<@NotNull Long> attachmentIds = List.of();
        @NotNull @Size(max=100) private List<@jakarta.validation.Valid Attachment> inlineFiles = List.of();
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class Query extends PageParam {
        @Size(max=100) private String keyword;
        @Pattern(regexp="DRAFT|PUBLISHED|OFFLINE") private String status;
        private Boolean pending;
    }
    @Data public static class Item {
        private Long id;
        private String title;
        private String content;
        private String status;
        private String publisherName;
        private LocalDateTime publishTime;
        private LocalDateTime acknowledgedTime;
        private Long recipientCount;
        private Long acknowledgedCount;
        private List<Long> targetUserIds;
        private List<Attachment> attachments = List.of();
    }
    @Data public static class Attachment {
        private Long id;
        private String name;
        private String type;
        private Long size;
        private String url;
    }
    @Data public static class Recipient {
        private Long userId;
        private String userName;
        private Long deptId;
        private String deptName;
        private LocalDateTime acknowledgedTime;
    }
    public record User(Long id, String name, Long deptId, boolean selectable) {}
    public record Department(Long id, String name, Long parentId) {}
    public record Options(List<Department> departments, List<User> users) {}
}
