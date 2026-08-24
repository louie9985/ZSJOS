package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StudentDeliveryStageSubmitReqVO {
    @NotBlank private String stage;
    @NotBlank @Size(max = 2000) private String remark;
    @NotNull private Boolean successful;
    private List<Long> attachmentFileIds;
    private Map<String, Object> data;
    @NotBlank @Size(max = 64) private String idempotencyKey;
}
