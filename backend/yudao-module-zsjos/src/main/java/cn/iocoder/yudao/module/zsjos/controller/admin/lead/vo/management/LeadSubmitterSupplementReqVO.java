package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class LeadSubmitterSupplementReqVO {
    /** Legacy fields remain deserializable during rolling deployment, but are ignored by the service. */
    private String provinceCode;
    private String cityCode;
    private String leadCategory;
    private List<Object> intendedProducts;
    @NotBlank @Size(max = 1000) private String remark;
    @Valid @Size(max = 9) private List<cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO> attachments;
    @NotBlank @Size(max = 128) private String idempotencyKey;
}
