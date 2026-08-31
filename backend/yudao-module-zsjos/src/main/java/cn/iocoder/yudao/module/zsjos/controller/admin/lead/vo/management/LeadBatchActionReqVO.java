package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class LeadBatchActionReqVO {
    @NotEmpty
    @Size(max = 100)
    private List<@NotNull @Positive Long> leadIds;

    @NotBlank
    @Size(max = 500)
    private String reason;

    private Long targetUserId;
    private Long collaboratorUserId;

    @NotBlank
    @Size(max = 40)
    private String idempotencyKey;
}
