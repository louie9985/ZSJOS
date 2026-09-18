package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

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

    @Schema(description = "到期时间，毫秒时间戳；未传时默认生成时刻起 7 天")
    private LocalDateTime expiresAt;
}
