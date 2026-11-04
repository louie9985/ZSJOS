package cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.List;

public final class ExamCollectionVO {
    private ExamCollectionVO() {}
    @Data public static class Create {
        @NotBlank @Size(max=200) private String title;
        @Size(max=2000) private String description;
        @NotNull @Size(max=12) private List<@NotBlank String> selectedFields;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class Query extends PageParam {
        @Size(max=200) private String keyword;
        @Pattern(regexp="OPEN|COMPLETED") private String status;
    }
    @Data public static class Task {
        private Long id;
        private String title, description, status;
        private Integer revision;
        private LocalDateTime createdAt, completedAt;
        private String completionReason;
        private long expected, collected, exceptions, additional;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class Detail extends Task {
        private List<Field> fields;
        private String url;
    }
}
