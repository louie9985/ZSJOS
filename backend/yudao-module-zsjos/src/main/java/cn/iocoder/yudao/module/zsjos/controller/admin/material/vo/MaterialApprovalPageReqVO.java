package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper = true)
public class MaterialApprovalPageReqVO extends PageParam {
    @NotBlank private String typeCode;
    private boolean done;
}
