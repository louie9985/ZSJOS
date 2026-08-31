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
    @Size(max = 100) private String keyword;
    @Pattern(regexp = "active|paused|completed", message = "学员服务状态不正确") private String serviceStatus;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
