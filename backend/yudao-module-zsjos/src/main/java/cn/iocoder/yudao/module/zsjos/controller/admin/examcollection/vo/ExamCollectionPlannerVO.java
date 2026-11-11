package cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

public final class ExamCollectionPlannerVO {
    private ExamCollectionPlannerVO() {}
    @Data @EqualsAndHashCode(callSuper=true)
    public static class Query extends PageParam {
        @Size(max=200) private String keyword;
        private Boolean filled;
        @jakarta.validation.constraints.Pattern(regexp="studentName|mobile|archiveName|archiveMobile|archiveIdCard|className|filled")
        private String sortField;
        @jakarta.validation.constraints.Pattern(regexp="asc|desc")
        private String sortOrder;
    }
    public record Member(Long serviceRelationId, String studentName, String mobile, String className, boolean filled, String archiveName, String archiveMobile, String archiveIdCard) {}
}
