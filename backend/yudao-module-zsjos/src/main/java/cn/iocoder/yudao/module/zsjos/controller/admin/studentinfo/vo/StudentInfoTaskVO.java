package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

public final class StudentInfoTaskVO {
    private StudentInfoTaskVO() { }
    @Data @EqualsAndHashCode(callSuper = true) public static class Query extends PageParam {
        private Long personId;
        @Size(max=100) private String keyword;
        @Pattern(regexp="pending_approval|revision_required|effective|terminated|superseded") private String orderStatus;
        @Pattern(regexp="PENDING|UNSENT|DRAFT|SUBMITTED|REUSED|CANCELLED") private String status;
    }
    @Data public static class Command {
        @NotNull private Integer version;
        @NotBlank @Size(max=64) private String idempotencyKey;
        @Size(max=13) private List<@NotBlank String> materialKeys;
        private Long configVersionId;
        private Long sourceFormId;
        private Integer sourceRevision;
    }
    @Data public static class GenerationConfig {
        private Long configVersionId;
        private List<StudentInfoVO.Field> fields;
        private List<String> selectableMaterialKeys;
        private List<String> defaultSelectedKeys;
    }
    @Data public static class Candidate {
        private Long formId;
        private String productName;
        private Integer revision;
        private List<String> missing;
    }
    @Data public static class Row {
        private Long id, orderId, orderItemId, personId, formId, registrationCaseId;
        private Integer version;
        private String orderNo, studentName, productName, orderStatus, status, archiveStatus;
        private List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO> specs;
        private Boolean canProcess;
        private List<Candidate> candidates;
    }
}
