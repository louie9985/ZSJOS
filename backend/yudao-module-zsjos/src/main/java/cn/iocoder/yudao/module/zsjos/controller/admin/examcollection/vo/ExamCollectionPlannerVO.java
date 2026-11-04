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
    }
    public record Member(Long serviceRelationId, String studentName, String mobile, String className, boolean filled) {}
}
