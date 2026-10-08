package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.Valid;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;

@Data
@EqualsAndHashCode(callSuper = true)
public class MyStudentPageReqVO extends PageParam {
    @jakarta.validation.constraints.Size(max = 64)
    private String sortField;
    @jakarta.validation.constraints.Pattern(regexp = "ascend|descend", message = "排序方向不正确")
    private String sortOrder;

    private String readScope;
    private Long targetUserId;
    @Size(max = 100) private String keyword;
    @Pattern(regexp = "active|paused|completed", message = "学员服务状态不正确") private String serviceStatus;
    private Long classId;
    private Boolean inServicePeriod;
    /** Media student list only: current operator on a service visible in this read scope. */
    @jakarta.validation.constraints.Positive
    private Long operatorUserId;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
