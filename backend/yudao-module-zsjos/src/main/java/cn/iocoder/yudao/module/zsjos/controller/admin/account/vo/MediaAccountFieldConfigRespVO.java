package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MediaAccountFieldConfigRespVO {
    private VersionVO published;
    private VersionVO draft;

    @Data
    public static class VersionVO {
        private Long id;
        private Integer versionNo;
        private String status;
        private LocalDateTime publishedAt;
        private Integer version;
        private List<FieldVO> fields;
    }

    @Data
    public static class FieldVO {
        private String key;
        private String label;
        private String type;
        private Boolean required;
        private Boolean enabled;
        private Integer sort;
        private String dictType;
        private Boolean searchable;
    }
}
