package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadInboxFilterSaveReqVO extends LeadInboxFilterConfigVO {

    @NotBlank(message = "客资收件箱视角不能为空")
    @Pattern(regexp = "submitter|owner|reviewer", message = "收件箱视角不正确")
    private String audience;
}
