package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerOpenRequestPageReqVO extends PageParam {

    @Size(max = 100)
    private String keyword;

    @Pattern(regexp = "pending|approved|opened|rejected|cancelled|open_failed", message = "申请状态无效")
    private String status;
}
