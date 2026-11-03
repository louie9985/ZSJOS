package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.util.*;

public final class StudentSummaryVO {
    private StudentSummaryVO() {}
    @Data public static class Query {
        @Min(1) private int pageNo = 1;
        @Min(1) @Max(100) private int pageSize = 20;
        @Positive private Long classId;
        @Size(max=100) private List<@Positive Long> classIds;
        @Size(max=100) private String keyword;
        private Boolean filled;
    }
    @Data public static class Export extends Query {
        @NotEmpty @Size(max=150) private List<@NotBlank String> columns;
    }
    public record Coverage(long submitted, long members) {}
    public record StatusOption(String text, String value) {}
    public record Column(String key, String label, String group, List<StatusOption> filters) {
        public Column(String key,String label,String group) { this(key,label,group,List.of()); }
    }
    public record Row(Long serviceRelationId, Map<String,String> values) {}
    public record ClassOption(Long value, String label) {}
}
