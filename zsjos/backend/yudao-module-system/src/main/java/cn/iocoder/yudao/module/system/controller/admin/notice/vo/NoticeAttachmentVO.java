package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "公告附件")
@Data
public class NoticeAttachmentVO {
    @NotNull(message = "附件文件编号不能为空")
    private Long infraFileId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private Integer sort;
    private String downloadUrl;
}
