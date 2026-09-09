package cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialImportPageReqVO extends PageParam {
    private Long materialTypeId;
    private String status;
}
