package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LeadCreateReqVO {
    @NotBlank @Size(max = 100) private String name;
    @Size(max = 32) private String mobile;
    @Size(max = 128) private String wechatId;
    @NotBlank @Size(max = 32) private String provinceCode;
    @NotBlank @Size(max = 32) private String cityCode;
    @Valid @Size(max = 20) private List<LeadProductReqVO> products;
    @Valid @Size(max = 20) private List<LeadProductReqVO> intendedProducts;
    @NotBlank @Size(max = 64) private String sourceChannel;
    @NotBlank @Size(max = 64) private String leadCategory;
    @Size(max = 1000) private String remark;
    @Valid @Size(max = 9) private List<LeadAttachmentReqVO> attachments = new ArrayList<>();
    @NotBlank private String dispatchMode;
    private Long specifiedSalesUserId;
    @NotBlank @Size(max = 128) private String idempotencyKey;

    public List<LeadProductReqVO> getEffectiveProducts() {
        return intendedProducts != null ? intendedProducts : products;
    }

    @AssertTrue(message = "至少添加一条意向课程")
    public boolean isProductSelectionValid() {
        return getEffectiveProducts() != null && !getEffectiveProducts().isEmpty() && getEffectiveProducts().size() <= 20;
    }
}
