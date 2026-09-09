package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialReferenceTargetPageReqVO extends PageParam {

    @Size(max = 100)
    private String keyword;
}
