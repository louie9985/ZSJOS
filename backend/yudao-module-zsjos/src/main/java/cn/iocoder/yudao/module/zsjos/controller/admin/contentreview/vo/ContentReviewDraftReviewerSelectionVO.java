package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Draft intent only; eligibility and the immutable reviewer snapshot are checked at submit. */
@Data
public class ContentReviewDraftReviewerSelectionVO {
    @NotNull
    @Positive
    private Long directorUserId;
    @NotBlank
    @Size(max = 255)
    private String processDefinitionId;
}
