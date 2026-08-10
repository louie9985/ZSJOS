package cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class UserRelationSaveReqVO {

    @NotBlank(message = "场景编码不能为空")
    private String sceneCode;

    @NotEmpty(message = "来源用户不能为空")
    private Set<Long> sourceUserIds;

    private Set<Long> targetUserIds;

    @NotBlank(message = "操作模式不能为空")
    private String mode;

}
