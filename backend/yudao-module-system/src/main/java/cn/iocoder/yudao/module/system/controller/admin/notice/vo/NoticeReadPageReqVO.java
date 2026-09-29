package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeReadPageReqVO extends PageParam {
    @NotNull private Long id;
    @NotBlank @Pattern(regexp = "EXPECTED|READ|UNREAD|EXTRA|ACTUAL")
    private String scope = "EXPECTED";
    @Size(max = 100) private String name;
    private Long deptId;
}
