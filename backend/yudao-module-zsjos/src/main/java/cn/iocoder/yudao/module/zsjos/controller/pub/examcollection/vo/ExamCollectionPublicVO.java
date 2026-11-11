package cn.iocoder.yudao.module.zsjos.controller.pub.examcollection.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
import java.util.Map;

public final class ExamCollectionPublicVO {
    private ExamCollectionPublicVO() {}
    @Data public static class Runtime {
        private String title, description, content, status;
        private List<Field> fields = List.of();
    }
    @Data public static class Session {
        private String sessionToken;
        private java.time.LocalDateTime expiresAt;
    }
    @Data public static class Submit {
        @NotBlank @Size(max=64) private String idempotencyKey;
        @NotNull @Size(max=16) private Map<String, Object> values;
    }
}
