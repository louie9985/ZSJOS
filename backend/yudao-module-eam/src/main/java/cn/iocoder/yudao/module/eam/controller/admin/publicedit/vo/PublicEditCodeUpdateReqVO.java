package cn.iocoder.yudao.module.eam.controller.admin.publicedit.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PublicEditCodeUpdateReqVO {

    @NotBlank(message = "口令不能为空")
    @Pattern(regexp = "[A-HJ-NP-Z2-9]{6}", message = "口令必须是 6 位大写英数字，且不能包含 I、O、0、1")
    private String code;

}
