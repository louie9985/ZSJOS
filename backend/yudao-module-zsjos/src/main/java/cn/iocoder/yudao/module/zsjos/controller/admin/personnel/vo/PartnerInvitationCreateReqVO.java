package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerInvitationCreateReqVO {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Mobile
    private String mobile;

    @NotNull
    private Long assignedOperatorUserId;
}
