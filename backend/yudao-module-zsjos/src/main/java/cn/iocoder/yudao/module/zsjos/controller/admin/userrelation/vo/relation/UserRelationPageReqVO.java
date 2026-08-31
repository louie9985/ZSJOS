package cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserRelationPageReqVO extends PageParam {

    @NotBlank(message = "场景编码不能为空")
    private String sceneCode;
    private String keyword;
    private Long deptId;
    private Boolean configured;

}
