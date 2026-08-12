package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class LeadSubmitterSupplementReqVO {
    @NotBlank @Size(max = 32) private String provinceCode;
    @NotBlank @Size(max = 32) private String cityCode;
    @NotBlank @Size(max = 64) private String leadCategory;
    @Valid @Size(min = 1, max = 20) private List<LeadProductReqVO> intendedProducts;
    @Size(max = 1000) private String remark;
    @NotBlank @Size(max = 128) private String idempotencyKey;
}
