package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RegistrationChecklistConfigRespVO {
    private Long templateId;
    private Integer templateVersion;
    private VersionVO published;
    private VersionVO draft;

    @Data
    public static class VersionVO {
        private Long id;
        private Integer versionNo;
        private String status;
        private LocalDateTime publishedAt;
        private List<ItemVO> items;
    }

    @Data
    public static class ItemVO {
        private Long id;
        private String itemKey;
        private String itemType;
        private String title;
        private Integer sort;
        private Boolean enabled;
        private Boolean systemRequired;
    }
}
