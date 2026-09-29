package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class CalendarMaintenanceNotifyReqVO {
    @NotBlank @Size(max = 128) private String operationKey;
    @NotNull @Min(0) private Integer expectedVersion;
    private boolean send = true;
    @NotBlank @Pattern(regexp = "SPECIFIED|ALL") private String scope = "SPECIFIED";
    private boolean originalRecipients;
    private List<@NotNull @Positive Long> userIds;
    private boolean resend;
    @Size(max = 128) private String previewToken;
}
