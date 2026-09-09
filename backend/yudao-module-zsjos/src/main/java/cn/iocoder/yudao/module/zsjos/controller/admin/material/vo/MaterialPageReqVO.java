package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialPageReqVO extends PageParam {
    @Size(max = 100) private String keyword;
    private Long materialTypeId;
    private Long accountId;
    private String status;
    private String source;
    private Boolean mine;
    private Boolean favorite;
    private Boolean recommendation;
    private String accountType;
    private String profession;
    private String accountStage;
}
