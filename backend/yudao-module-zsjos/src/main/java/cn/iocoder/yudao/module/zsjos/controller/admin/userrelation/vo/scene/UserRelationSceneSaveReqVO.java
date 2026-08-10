package cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserRelationSceneSaveReqVO {

    private Long id;

    @NotBlank(message = "场景名称不能为空")
    private String name;

    @NotBlank(message = "场景编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{2,63}$", message = "场景编码只能使用小写字母、数字和下划线，并以字母开头")
    private String code;

    @NotBlank(message = "来源用户称谓不能为空")
    private String sourceLabel;

    @NotBlank(message = "目标用户称谓不能为空")
    private String targetLabel;

    @NotBlank(message = "来源岗位不能为空")
    private String sourcePostCode;

    @NotBlank(message = "目标岗位不能为空")
    private String targetPostCode;

    @NotNull(message = "状态不能为空")
    private Integer status;

    private String remark;

}
