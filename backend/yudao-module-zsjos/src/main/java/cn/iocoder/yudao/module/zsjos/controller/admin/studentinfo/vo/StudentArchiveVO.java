package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;

public interface StudentArchiveVO {
    @Data class Detail {
        private Long personId, serviceRelationId, registrationCaseId, orderItemId;
        private String productName;
        private Integer revision;
        private Boolean canMaintain;
        private StudentInfoVO.Detail information;
        private List<History> history = List.of();
    }
    @Data class History {
        private Integer revision;
        private Long operatorUserId;
        private LocalDateTime modifiedAt;
        private String reason;
        private String operatorName;
    }
    @Data class Edit {
        private Integer revision;
        private Map<String, Object> values = Map.of();
        private Map<String, List<StudentInfoVO.Option>> options = Map.of();
        private List<StudentInfoVO.Image> images = List.of();
    }
    @Data class Save {
        @NotNull @Min(0) private Integer revision;
        @NotBlank @Size(max=64) private String idempotencyKey;
        @NotBlank @Size(max=500) private String reason;
        @NotNull @Size(max=32) private Map<String, Object> changes;
    }
}
