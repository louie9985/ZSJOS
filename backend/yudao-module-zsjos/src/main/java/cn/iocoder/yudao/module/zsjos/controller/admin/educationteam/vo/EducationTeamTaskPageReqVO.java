package cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EducationTeamTaskPageReqVO extends PageParam {
    @Pattern(regexp = "overdue|today|future|unscheduled", message = "待办时间筛选无效")
    private String bucket;
}
