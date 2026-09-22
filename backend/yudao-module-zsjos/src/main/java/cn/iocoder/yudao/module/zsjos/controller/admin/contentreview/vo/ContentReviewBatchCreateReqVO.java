package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ContentReviewBatchCreateReqVO {
    @NotEmpty(message = "请选择内容版本")
    @Size(min = 1, max = 20, message = "每批次必须包含 1 至 20 件作品")
    private List<Long> contentVersionIds;

    /** 学员概览入口的业务上下文；旧版按内容版本组批时可为空。 */
    private Long studentPersonId;

    @Size(max = 20, message = "发布账号最多选择 20 个")
    private List<Long> accountIds;
}
