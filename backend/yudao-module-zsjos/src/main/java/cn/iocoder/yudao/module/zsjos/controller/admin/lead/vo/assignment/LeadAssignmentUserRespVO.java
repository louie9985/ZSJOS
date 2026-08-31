package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment;

import cn.iocoder.yudao.framework.desensitize.core.slider.annotation.MobileDesensitize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 派单关系用户 Response VO")
@Data
public class LeadAssignmentUserRespVO {

    private Long id;
    private String nickname;
    @MobileDesensitize
    private String maskedMobile;
    private Long deptId;
    private String deptName;
    private String avatar;
    private Integer status;

}
