package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LeadComplaintCreateReqVO {
    @NotBlank @Size(max = 1000) private String reason;
    @Size(max = 9) private List<Long> evidenceFileIds = new ArrayList<>();
    @NotBlank @Size(max = 128) private String idempotencyKey;
}
