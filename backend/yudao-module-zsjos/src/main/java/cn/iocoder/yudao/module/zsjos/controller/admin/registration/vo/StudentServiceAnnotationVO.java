package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

public interface StudentServiceAnnotationVO {
    @Data class Save {
        @NotNull @Min(0) private Integer version;
        @Pattern(regexp = "true|false") private String proxyStudent;
        @Size(max = 100) private String agentName;
        @Pattern(regexp = "true|false") private String serviceNeeded;
    }
    @Data class Detail {
        private Long serviceRelationId;
        private int version;
        private String proxyStudent;
        private String proxyStudentLabel;
        private String agentName;
        private String serviceNeeded;
        private String serviceNeededLabel;
        private boolean canEdit;
    }
    record Option(String value, String label) { }
}
